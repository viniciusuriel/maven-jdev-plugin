package org.apache.myfaces.trinidadbuild.plugin.jdeveloper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;


/**
 * Generates the Ant build file to compile ADF Declaractive Component from a
 * maven development environment.
 * 
 * @goal dec
 * @execute phase=process-resources
 * @requiresDependencyResolution test
 * @description Goal which generates the Ant build file to compile ADF
 *              Declaractive Component from a maven development environment.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class JDeveloperDeclaractiveComponentMojo extends AbstractMojo {
	
	
	private static final String RESOURCE_PACKAGE = "org/apache/myfaces/trinidadbuild/plugin/jdeveloper/ant/";

	/**
	 * The JDeveloper Home.
	 * @parameter expression="${dec.home}"
	 */
	private String home;

	/**
	 * The workspace name.
	 * 
	 * @parameter expression="${dec.workspace}"
	 * @required
	 */
	private String workspace;

	/**
	 * Name of the Maven Project
	 * 
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	private MavenProject project;
	
	private static final String REPLACE_WINDOWS_SEPARATOR = "\\\\";

	public void execute() throws MojoExecutionException, MojoFailureException {
		generateBuildPropertyFile();
		try {
			generateAntBuildFile(this.project);
		} catch (IOException exception) {
			throw new MojoExecutionException("Error in generate build.xml file", exception);
		}
	}

	private void generateAntBuildFile(MavenProject project) throws IOException{
		// include compile, provided, runtime and system scopes.
		Set compileArtifacts = new LinkedHashSet();
		compileArtifacts.addAll(project.getCompileArtifacts());
		compileArtifacts.addAll(project.getRuntimeArtifacts());
		
		File projectDir = project.getBasedir();
		String template = readTemplateBuildFile();
		
		//TODO Replace source version and target version dynamically
		template = template.replaceAll("_artifactId_", project.getArtifactId()).
				   replaceAll("_encoding_", System.getProperty("project.build.sourceEncoding", "ISO-8859-1")).
				   replaceAll("_source_", "1.6").
				   replaceAll("_target_", "1.6").
				   replaceAll("_java_path_", project.getBuild().getSourceDirectory().replaceAll(REPLACE_WINDOWS_SEPARATOR, "/"));
		
		String classpath = createClassPathOf(projectDir, new ArrayList(compileArtifacts));
		template = template.replaceAll("__artifacts__", classpath);
		this.writeFile(getBuildFile(project), template);
	}

	private void generateBuildPropertyFile() throws MojoExecutionException{
		
		final String JDEV_HOME = "_jdev_home_";
		final String EQUALS = "=";
		final String NEW_LINE = "\n";
		
		String profileName = this.project.getBuild().getFinalName().replaceAll("\\.", "_").replaceAll("-", "_");
		
		File propertyFile = this.getPropertyFile(this.project);
		List<String[]> propertyEntries = this.getBuildPropertyEntries(this.project);
		StringBuilder sb = new StringBuilder();	
		
		if (getJDevHome() == null)
			throw new MojoExecutionException("JDev home (JDEV_HOME) variable is null.");
		
		if (this.getWorkspace() == null){
			throw new MojoExecutionException("Workspace variable is null.");
		}
				
		for(String[] entry : propertyEntries){			
			String value = entry[1].replaceAll(JDEV_HOME , getJDevHome()).
						   replaceAll("_workspace_dir_", this.getWorkspace()).
						   replaceAll("_output_dir_", this.project.getBuild().getOutputDirectory().replaceAll(REPLACE_WINDOWS_SEPARATOR, "/")).
						   replaceAll("_deploy_dir_", this.project.getBasedir().getPath().replaceAll(REPLACE_WINDOWS_SEPARATOR, "/") + "/target/").
						   replaceAll("_profile_name_", profileName).
						   replaceAll("_artifact_id_", this.project.getArtifactId()).
						   replaceAll("_build_file_name_", this.project.getArtifactId() + "-" + this.project.getVersion() + ".jar");						   
			
			sb.append(entry[0]).append(EQUALS).append(value).append(NEW_LINE);
		}
		writeFile(propertyFile, sb.toString());		
	}

	/**
	 * Returns the build properties file for a Ant build.
	 * 
	 * @param project
	 *            the Maven POM
	 * 
	 * @return the build properties file.
	 */
	private File getPropertyFile(MavenProject project) {
		return new File(project.getBasedir(), "build.properties");
	}
	
	/**
	 * Returns the build xml file for a Ant build.
	 * 
	 * @param project
	 *            the Maven POM
	 * 
	 * @return the build xml file.
	 */
	private File getBuildFile(MavenProject project) {
		return new File(project.getBasedir(), "build.xml");
	}

	/**
	 * @param project the Maven POM
	 * @return The entries to be put in {@link Properties} file.
	 */
	private List<String[]> getBuildPropertyEntries(MavenProject project) {
		final List<String[]> entries = new ArrayList<String[]>();
		Properties defaultProperties = new Properties();
		try {
			defaultProperties.load(this.getClass().getClassLoader().getResourceAsStream(RESOURCE_PACKAGE + "build.properties"));
			for (Enumeration<Object> keys = defaultProperties.keys(); keys.hasMoreElements();) {
				String key = keys.nextElement().toString();
				entries.add(new String[] {key, defaultProperties.getProperty(key)});
			}
		} catch (IOException exception) {
			this.getLog().error(exception);
		}
		return Collections.unmodifiableList(entries);
	}
	
	private String readTemplateBuildFile() throws IOException{
		StringBuilder content = new StringBuilder();
		
		InputStream is = null;
		InputStreamReader reader = null;
		BufferedReader bfReader = null; 
			
		try{
			
			is = this.getClass().getClassLoader().getResourceAsStream(RESOURCE_PACKAGE + "build.xml");
			reader = new InputStreamReader(is);
			bfReader = new BufferedReader(reader);
			
			String line;
			while((line = bfReader.readLine()) != null){
				content.append(line).append("\n");
			}
			
		}finally{
			
			if (is != null)
				is.close();
			
			if (reader != null)
				reader.close();
			
			if (bfReader != null)
				bfReader.close();
		}
		return content.toString();
	}
	
	private String createClassPathOf(File projectDir, List artifacts) {
		StringBuilder sb = new StringBuilder();
		
		Collections.sort(artifacts, new Comparator()
        {
          public int compare(Object a, Object b)
          {
            Artifact arta = (Artifact) a;
            Artifact artb = (Artifact) b;
            return arta.getId().compareTo(artb.getId());
          }
        });
		
		for(Iterator iter = artifacts.iterator(); iter.hasNext();)
		{
			Artifact artifact = (Artifact) iter.next();
	        String path = Files.getRelativeFile(projectDir, artifact.getFile());
	        sb.append(String.format("<pathelement location=\"%s\"/>\n", path));
		}
		return sb.toString();
	}
	
	
	/**
	 * Write a content in a given {@link File}
	 * 
	 * @param file {@link File} to be write.
	 * @param content The content to be write in the given {@link File}.
	 */
	private void writeFile(File file, String content) {
		BufferedWriter writer = null;
		
		try {
			file.createNewFile();
			writer = new BufferedWriter(new FileWriter(file));
			writer.write(content);
		} catch (IOException exception) {
			this.getLog().error("Error in writer file: " + file.getAbsolutePath(), exception);
		}finally{
			if (writer != null)
				try {
					writer.close();
				} catch (IOException ignore) {
					this.getLog().debug(ignore);
				}
		}
	}
	
	private String getJDevHome() {
		String jdev = this.home;
		
		if (jdev == null || jdev.trim().length() == 0){
			jdev = (this.project.getProperties().getProperty("jdev.home"));
			
			if (jdev == null)
				jdev = System.getProperty("jdev.home");
			
			if (jdev == null)
				jdev = System.getenv("JDEV_HOME");			
		}			
		return replaceSeparator(jdev);
	}
	
	private String getWorkspace() {
		String workspace = this.workspace;
		
		if (workspace == null || workspace.trim().length() == 0)
		{
			workspace = (this.project.getProperties().getProperty("workspace"));
			
			if (workspace == null)
				workspace = System.getProperty("workspace");
			
			if (workspace == null)
				workspace = System.getenv("workspace");			
		}		
		return replaceSeparator(workspace);
	}	
	
	String replaceSeparator(String value){
		return (value == null) ? null : value.replaceAll("\\\\", "/");
	}
}