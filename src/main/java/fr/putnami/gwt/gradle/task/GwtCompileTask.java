/**
 * This file is part of pwt.
 *
 * pwt is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * pwt is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with pwt. If not,
 * see <http://www.gnu.org/licenses/>.
 */
package fr.putnami.gwt.gradle.task;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.List;
import java.util.concurrent.Callable;

import fr.putnami.gwt.gradle.action.JavaAction;
import fr.putnami.gwt.gradle.extension.CompilerOption;
import fr.putnami.gwt.gradle.extension.PutnamiExtension;
import fr.putnami.gwt.gradle.helper.CompileCommandBuilder;

public class GwtCompileTask extends AbstractTask {

	public static final String NAME = "gwtCompile";

	private List<String> modules;
	private File war;
	private FileCollection src;

	public GwtCompileTask() {
		setName(NAME);
		setDescription("Compile the GWT modules");

		dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaPlugin.PROCESS_RESOURCES_TASK_NAME);
	}

	@TaskAction
	public void exec() {

		PutnamiExtension putnami = getProject().getExtensions().getByType(PutnamiExtension.class);
		CompilerOption compilerOptions = putnami.getCompile();

		CompileCommandBuilder commandBuilder = new CompileCommandBuilder();
		commandBuilder.configure(getProject(), compilerOptions, getSrc(), getWar(), getModules());
		JavaAction compileAction = commandBuilder.buildJavaAction();
		compileAction.execute(this);
		compileAction.join();
		if (compileAction.exitValue() != 0) {
			throw new RuntimeException("Fail to compile GWT modules");
		}

		getProject().getTasks().getByName(GwtCheckTask.NAME).setEnabled(false);
	}

	public void configure(final Project project, final PutnamiExtension extention) {
		final CompilerOption options = extention.getCompile();

		options.init(getProject());
		options.setLocalWorkers(evalWorkers(options));

		JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
		SourceSet mainSourceSet = javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
		final FileCollection sources = getProject()
			.files(project.files(mainSourceSet.getOutput().getResourcesDir()))
			.plus(project.files(mainSourceSet.getOutput().getClassesDir()))
			.plus(getProject().files(mainSourceSet.getAllSource().getSrcDirs()));

		ConventionMapping mapping = ((IConventionAware) this).getConventionMapping();

		mapping.map("modules", new Callable<List<String>>() {
			@Override
			public List<String> call() throws Exception {
				return extention.getModule();
			}
		});
		mapping.map("war", new Callable<File>() {
			@Override
			public File call() throws Exception {
				return options.getWar();
			}
		});
		mapping.map("src", new Callable<FileCollection>() {
			@Override
			public FileCollection call() throws Exception {
				return sources;
			}
		});
	}

	private int evalWorkers(CompilerOption options) {
		long workers = Runtime.getRuntime().availableProcessors();
		OperatingSystemMXBean osMBean = ManagementFactory.getOperatingSystemMXBean();
		if (osMBean instanceof com.sun.management.OperatingSystemMXBean) {
			com.sun.management.OperatingSystemMXBean sunOsMBean = (com.sun.management.OperatingSystemMXBean) osMBean;
			long memPerWorker = 1024L * 1024L * options.getLocalWorkersMem();
			long nbFreeMemInGb = sunOsMBean.getFreePhysicalMemorySize() / memPerWorker;

			if (nbFreeMemInGb < workers) {
				workers = nbFreeMemInGb;
			}
			if (workers < 1) {
				workers = 1;
			}
		}
		return (int) workers;
	}

	@OutputDirectory
	public File getWar() {
		return war;
	}

	@Input
	public List<String> getModules() {
		return modules;
	}

	@InputFiles
	public FileCollection getSrc() {
		return src;
	}
}
