/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.myfaces.trinidadbuild.plugin.jdeveloper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.RuntimeInformation;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.xml.sax.SAXException;

/*
 * Important informational note:
 *
 * The following XML code is similar to what would be seen in a pom file
 * under the maven-jdev-plugin tag if Libraries (e.g. JSP Runtime
 * and JSF 1.2) and Distributed Tag Libraries wanted to be added to the .jpr
 * project.
 *
 * What you see below are the defaults that are put in a project's .jpr
 * automatically without specifying anything.  See replaceLibraries(),
 * replaceTagLibraries() and replaceDefaultTagLibraries().
 *
 * Also note that by setting the property "jdev.plugin.add.libraries" to
 * false in a project's pom.xml file, no libraries will be added, default
 * or otherwise.  In kind, setting the property "jdev.plugin.add.taglibs"
 * to false will result in no tag libraries of any kind being added.
 *
 * As you would expect, the default for both of these properties is "true".
 *
 *   <plugin>
      <groupId>org.apache.myfaces.trinidadbuild</groupId>
      <artifactId>maven-jdev-plugin</artifactId>
      <version>1.2.4-SNAPSHOT</version>
      <inherited>true</inherited>
      <configuration>
        <libraries>
          <library>JSP Runtime</library>
          <library>JSF 1.2</library>
        </libraries>
        <distributedTagLibraries>
          <distributedTagLibrary>
            <property>
              <name>name</name>
              <value>JSF HTML</value>
            </property>
            <property>
              <name>version</name>
              <value>1.2</value>
            </property>
            <property>
              <name>jspVersion</name>
              <value>2.1</value>
            </property>
            <property>
              <name>tld</name>
              <value>html_basic.tld</value>
            </property>
            <property>
              <name>URI</name>
              <value>http://java.sun.com/jsf/html</value>
            </property>
          </distributedTagLibrary>
          <distributedTagLibrary>
            <property>
              <name>name</name>
              <value>JSF Core</value>
            </property>
            <property>
              <name>version</name>
              <value>1.2</value>
            </property>
            <property>
              <name>jspVersion</name>
              <value>2.1</value>
            </property>
            <property>
              <name>tld</name>
              <value>jsf_core.tld</value>
            </property>
            <property>
              <name>URI</name>
              <value>http://java.sun.com/jsf/core</value>
            </property>
          </distributedTagLibrary>
        </distributedTagLibraries>
      </configuration>
    </plugin>
 */

/**
 * Generates the JDeveloper Workspace and Projects from a maven development
 * environment.
 * 
 * @version $Id$
 * @goal jdev
 * @execute phase=process-resources
 * @requiresDependencyResolution test
 * @description Goal which generates the JDeveloper Workspace and Projects from
 *              a maven development environment.
 */
public class JDeveloperMojo extends AbstractMojo {
	
	/**
	 * Libraries to include in Classpath.
	 * 
	 * @parameter
	 */
	private String[] libraries;

	/**
	 * Techonologies to include in jpr.
	 * 
	 * @parameter
	 */
	private String[] technologiesScope;
	
	/**
	 * @parameter expression="${webProject}" default-value="false"
	 */
	private boolean webProject;
	
	/**
	 * @parameter expression="${includeTagLibrariesOfPackage}"
	 */
	@Deprecated
	private String includeTagLibrariesOfPackage;
	
	/**
	 * Tag libraries packages
	 * @parameter 
	 */
	private String[] includeTagLibrariesOfPackages;

	/**
	 * List of source root directories
	 * 
	 * @parameter
	 */
	private File[] sourceRoots;

	/**
	 * Does project have tests? Used to determine if the maven-jdev-plugin needs
	 * to create a <project name>-test.jpr
	 * 
	 * @parameter expression="${jdev.project.has.tests}" default-value="false"
	 */
	private boolean projectHasTests;

	/**
	 * List of source root directories for the test project
	 * 
	 * @parameter
	 */
	private File[] testSourceRoots;

	/**
	 * List of resource root directories
	 * 
	 * @parameter
	 */
	private File[] resourceRoots;

	/**
	 * List of resource root directories for the test project
	 * 
	 * @parameter
	 */
	private File[] testResourceRoots;

	/**
	 * Force the Mojo to use the default project.xml or workspace.xml file to be
	 * used in creation of the workspace file (.jws) or project file (.jpr).
	 * Otherwise, if a .jws or .jpr exists, it will be used instead.
	 * 
	 * @parameter expression="${force}" default-value=false
	 * 
	 */
	private boolean force;

	/**
	 * @parameter expression="${declaractive}" default-value=false
	 */
	private boolean declaractive;
	
	/**
	 * @parameter expression="${remoteDebuggingProfiling}" default-value=false
	 */
	private boolean remoteDebuggingProfiling;

	/**
	 * Compiler to be used by JDeveloper. "Ojc" is the default. If this
	 * parameter is absent or anything other than "Javac", "Ojc" will be used.
	 * 
	 * @parameter expression="${jdev.compiler}" default-value="Ojc"
	 */
	private String compiler;

	/**
	 * Make the entire project before each run. Running anything in the project
	 * will automatically trigger a make of the entire project followed by the
	 * run.
	 * 
	 * @parameter expression="${jdev.make.project}" default-value="false"
	 */
	private boolean makeProject;

	/**
	 * Default file to be run when JDeveloper project is run.
	 * 
	 * @parameter expression="${jdev.run.target}"
	 */
	private String runTarget;

	/**
	 * Create JDeveloper Workspace and Project Files that correspond to the
	 * format used
	 * 
	 * @parameter expression="${jdev.release}" default-value="10.1.3.0.4"
	 */
	private String release;

	/**
	 * Name of the Maven Project
	 * 
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	private MavenProject project;

	/**
	 * List of reactorProjects
	 * 
	 * @parameter expression="${reactorProjects}"
	 * @required
	 * @readonly
	 */
	private List reactorProjects;

	/**
	 * Tag library directory used by each distributed jsf tag library
	 * 
	 * @parameter expression="${jdev.tag.lib.dir}"
	 */
	private String tagLibDirectory;

	/**
	 * Tag libraries and their properties.
	 * 
	 * @parameter
	 */
	private Properties[] distributedTagLibraries;

	/**
	 * JVM javaOptions found under Project Properties->Run/Debug/Profile->Launch
	 * Settings->Java Options in JDeveloper. "-ea" is the default for
	 * "enableAssertions" and -Djava.net.preferIPv4Stack=true for enable IPv4 stack.
	 * 
	 * @parameter expression="${jdev.jvm.java.options}" default-value="-ea -Djava.net.preferIPv4Stack=true"
	 */
	private String javaOptions;
	
	
	 /**
     * Enables/disables the downloading of source attachments. Defaults to false. When this flag is <code>true</code>
     * remote repositories are checked for sources: in order to avoid repeated check for unavailable source archives, a
     * status cache is mantained.
     *  
     * @parameter expression="${downloadSources}" default-value="false"
     */
    protected boolean downloadSources;

    /**
     * Enables/disables the downloading of javadoc attachments. Defaults to false. When this flag is <code>true</code>
     * remote repositories are checked for javadocs: in order to avoid repeated check for unavailable javadoc archives,
     * a status cache is mantained.
     *  
     * @parameter expression="${downloadJavadocs}"
     */
    protected boolean downloadJavadocs;
	
    /**
     * Artifact factory, needed to download source jars for inclusion in classpath.
     * 
     * @component role="org.apache.maven.artifact.factory.ArtifactFactory"
     * @required
     * @readonly
     */
    protected ArtifactFactory artifactFactory;

    /**
     * Artifact resolver, needed to download source jars for inclusion in classpath.
     * 
     * @component role="org.apache.maven.artifact.resolver.ArtifactResolver"
     * @required
     * @readonly
     */
    protected ArtifactResolver artifactResolver;

    /**
     * Artifact collector, needed to resolve dependencies.
     * 
     * @component role="org.apache.maven.artifact.resolver.ArtifactCollector"
     * @required
     * @readonly
     */
    protected ArtifactCollector artifactCollector;

    /**
     * @component role="org.apache.maven.artifact.metadata.ArtifactMetadataSource" hint="maven"
     */
    protected ArtifactMetadataSource artifactMetadataSource;

    /**
     * The runtime information for Maven, used to retrieve Maven's version number.
     * 
     * @component
     */
    private RuntimeInformation runtimeInformation;

    /**
     * Remote repositories which will be searched for source attachments.
     * 
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    protected List remoteArtifactRepositories;

    /**
     * Local maven repository.
     * 
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    protected ArtifactRepository localRepository;

	/**
	 * Execute the Mojo.
	 */
	public void execute() throws MojoExecutionException {
		_parseRelease();

		if (_releaseMajor >= 11) {
			tagLibDirectory =
			// "@oracle.home@/modules/oracle.jsf_1.2.9/glassfish.jsf_1.2.9.0.jar!/META-INF";
			"@oracle.home@/../oracle_common/modules/oracle.jsf_2.0/jsf-impl.jar!/META-INF";
		} else {
			tagLibDirectory = "@oracle.home@lib/java/shared/oracle.jsf/1.2/jsf-ri.jar!/META-INF";
		}

		try {
			generateWorkspace();
			generateProject();
			generateTestProject();
		} catch (IOException e) {
			throw new MojoExecutionException(e.getMessage());
		}
	}

	private void generateWorkspace() throws MojoExecutionException, IOException {
		if (project.getCollectedProjects().isEmpty() && declaractive) {
			getLog().info(
					"Generating JDeveloper " + release + " workspace: "
							+ project.getArtifactId());

			File workspaceFile = getJWorkspaceFile(project);

			try {
				Xpp3Dom workspaceDOM = readWorkspaceDOM(workspaceFile);
				replaceProjects(workspaceFile.getParentFile(), workspaceDOM);
				writeDOM(workspaceFile, workspaceDOM);
			} catch (XmlPullParserException e) {
				throw new MojoExecutionException("Error generating workspace",
						e);
			}
		}
	}

	private void generateProject() throws IOException, MojoExecutionException {
		if (!"pom".equals(project.getPackaging())) {
			File projectFile = getJProjectFile(project);

			// Get Project Properties to tell Mojo whether or not to add
			// library refs and taglibs to the project.
			Properties props = project.getProperties();
			String addLibs = (String) props.get(_PROPERTY_ADD_LIBRARY);
			String addTagLibs = (String) props.get(_PROPERTY_ADD_TAGLIBS);
			
			final boolean webProject = this.isWebProject();
			
			_addLibraries = (addLibs == null) ? true : (new Boolean(addLibs)).booleanValue();
			_addLibraries = _addLibraries & webProject; 
				
			_addTagLibs = (addTagLibs == null) ? true : (new Boolean(addTagLibs)).booleanValue();
			_addTagLibs = _addTagLibs && webProject;

			// TODO: read configuration for war:war goal
			File webappDir = new File(project.getBasedir(), "src/main/webapp");
			// TODO: read configuration for compiler:complie goal
			File outputDir = new File(project.getBuild().getDirectory(), "classes");

			MavenProject executionProject = project.getExecutionProject();
			List compileSourceRoots = executionProject.getCompileSourceRoots();
			if (sourceRoots != null) {
				for (int i = 0; i < sourceRoots.length; i++) {
					compileSourceRoots.add(sourceRoots[i].getAbsolutePath());
				}
			}

			List compileResourceRoots = executionProject.getResources();
			if (resourceRoots != null) {
				for (int i = 0; i < resourceRoots.length; i++) {
					Resource resource = new Resource();
					resource.setDirectory(resourceRoots[i].getAbsolutePath());
					compileResourceRoots.add(resource);
				}
			}

			getLog().info("Generating JDeveloper " + release + " Project " + project.getArtifactId());

			Set pluginArtifacts = new LinkedHashSet();
			pluginArtifacts.addAll(project.getPluginArtifacts());

			// Note: include "compile", "provided", "system" and "runtime"
			// scopes
			Set compileArtifacts = new LinkedHashSet();
			compileArtifacts.addAll(project.getCompileArtifacts());
			compileArtifacts.addAll(project.getRuntimeArtifacts());

			// Note: separate "runtime" vs. "compile" dependencies in
			// JDeveloper?
			generateProject(projectFile, project.getArtifactId(),
					project.getPackaging(), project.getDependencies(),
					new ArrayList(compileArtifacts), compileSourceRoots,
					compileResourceRoots,
					Collections.singletonList(webappDir.getPath()), outputDir);
		}
	}

	private void generateTestProject() throws IOException,
			MojoExecutionException {
		if (!"pom".equals(project.getPackaging()) && projectHasTests) {
			File projectFile = getJProjectTestFile(project);

			// Get Project Properties to tell Mojo whether or not to add
			// library refs and taglibs to the project.
			Properties props = project.getProperties();
			String addLibs = (String) props.get(_PROPERTY_ADD_LIBRARY);
			String addTagLibs = (String) props.get(_PROPERTY_ADD_TAGLIBS);
			_addLibraries = (addLibs == null) ? true : (new Boolean(addLibs)).booleanValue();
			_addTagLibs = (addTagLibs == null) ? true: (new Boolean(addTagLibs)).booleanValue();
			
			File webappDir = new File(project.getBasedir(), "src/test/webapp");
			// TODO: read configuration for compiler:testCompile goal
			File outputDir = new File(project.getBuild().getDirectory(),
					"test-classes");

			// self dependency needed for test project
			List testDependencies = new ArrayList(project.getTestDependencies());
			Dependency selfDependency = new Dependency();
			selfDependency.setArtifactId(project.getArtifactId());
			selfDependency.setGroupId(project.getGroupId());
			selfDependency.setType(project.getPackaging());
			testDependencies.add(selfDependency);

			MavenProject executionProject = project.getExecutionProject();
			List compileSourceRoots = executionProject
					.getTestCompileSourceRoots();

			if (testResourceRoots != null) {
				for (int i = 0; i < testSourceRoots.length; i++) {
					compileSourceRoots
							.add(testSourceRoots[i].getAbsolutePath());
				}
			}

			List compileResourceRoots = executionProject.getTestResources();
			if (testResourceRoots != null) {
				for (int i = 0; i < testResourceRoots.length; i++) {
					Resource resource = new Resource();
					resource.setDirectory(testSourceRoots[i].getAbsolutePath());
					compileResourceRoots.add(resource);
				}
			}

			getLog().info(
					"Generating JDeveloper " + release + " Project "
							+ project.getArtifactId() + "-test");

			// Note: all artifacts implicitly included in "test" scope
			generateProject(projectFile, project.getArtifactId() + "-test",
					project.getPackaging(), testDependencies,
					project.getTestArtifacts(), compileSourceRoots,
					compileResourceRoots,
					Collections.singletonList(webappDir.getPath()), outputDir);
		}
	}

	private void generateProject(File projectFile, String projectName,
			String packaging, List dependencies, List artifacts,
			List sourceRoots, List resourceRoots, List webSourceRoots,
			File outputDir) throws IOException, MojoExecutionException {
		try {
			final File projectDir = projectFile.getParentFile();
			Xpp3Dom projectDOM = readProjectDOM(projectFile);
			replaceWebappInfo(projectName, projectDOM);
			replaceSourcePaths(projectDir, sourceRoots, resourceRoots, projectDOM);
			replaceResourcePaths(projectDir, resourceRoots, projectDOM);
			replaceWebSourcePaths(projectDir, webSourceRoots, projectDOM);
			//Não fazer referencia a outros projetos
			//replaceDependencies(projectDir, dependencies, projectDOM);

			if (this.declaractive) {
				replaceTechnologiesScopes(projectName, projectDOM);
			} 

			replaceLibraries(projectDir, artifacts, projectDOM);
			replaceTagLibraries(projectDOM, projectDir, dependencies, artifacts);
			replaceOutputDirectory(projectDir, outputDir, projectDOM);
			replaceParameters(projectName, projectDOM);

			if (this.declaractive) {
				replaceDeploymentProfiles(projectName, projectDOM);
			} else {
				Xpp3Dom deployDOM = findNamedChild(projectDOM, "hash", "oracle.jdeveloper.deploy.dt.DeploymentProfiles");
				this.removeChildren(deployDOM);
			}

			this.replaceMavenRunConfiguration(projectDir, projectName, projectDOM);
			writeDOM(projectFile, projectDOM);

			/*
			 * Not needed in release 11 where .tld files are obtained* From
			 * jar's META-INF dir, not in WEB-INF dir as in v10
			 */
			if ((_releaseMajor < 11) && ("war".equals(packaging)))
				copyTagLibraries(projectDir, dependencies, artifacts);

		} catch (XmlPullParserException e) {
			throw new MojoExecutionException("Error generating project", e);
		}
	}

	private void replaceProjects(File workspaceDir, Xpp3Dom workspaceDOM)
			throws XmlPullParserException {
		// /jws:workspace
		// /list[@n="listOfChildren"]
		Xpp3Dom sourceDOM = workspaceDOM.getChild("list");

		// <hash>
		// <value n="nodeClass" v="oracle.jdeveloper.model.JProject"/>
		// <url n="URL" path="[workspace-relative-path-to-project.jpr]"/>
		// </hash>
		Xpp3Dom targetDOM = new Xpp3Dom("list");


			MavenProject collectedProject = (MavenProject) project;
			boolean projHasTests = false;

			// Added in V11
			if (_releaseMajor >= 11) {
				Properties props = collectedProject.getProperties();
				String hasTests = (String) props.get(_PROPERTY_HAS_TESTS);
				projHasTests = "true".equalsIgnoreCase(hasTests);
			}

			getLog().info(
					"projHasTests is "
							+ Boolean.valueOf(projHasTests).toString());

			
				File projectFile = getJProjectFile(collectedProject);

				targetDOM.addChild(createProjectReferenceDOM(workspaceDir,
						projectFile));

				File testProjectFile = getJProjectTestFile(collectedProject);

				/*
				 * * In V11, we don't create a <projname>-test.jpr if* a project
				 * does not have any tests.
				 */
				if (_releaseMajor >= 11) {
					if (projHasTests) {
						targetDOM.addChild(createProjectReferenceDOM(
								workspaceDir, testProjectFile));
					}
				} else {
					targetDOM.addChild(createProjectReferenceDOM(workspaceDir,
							testProjectFile));
				}

		// TODO: use a better merge algorithm
		removeChildren(sourceDOM);

		// make sure to pass Boolean.FALSE to allow
		// multiple child elements with the same name
		Xpp3Dom.mergeXpp3Dom(sourceDOM, targetDOM, Boolean.FALSE);
	}

	private void replaceWebSourcePaths(File projectDir, List sourceRoots, Xpp3Dom projectDOM) throws XmlPullParserException {
		
		// /jpr:project
		// /hash[@n="oracle.jdeveloper.model.J2eeSettings"]
		// /hash[@n="webContentSet"]
		// /list[@n="url-path"]
		Xpp3Dom pathsDOM = findNamedChild(projectDOM, "hash", "oracle.jdeveloper.model.J2eeSettings");
		Xpp3Dom contentSetDOM = findNamedChild(pathsDOM, "hash", "webContentSet");
		Xpp3Dom sourceDOM = findNamedChild(contentSetDOM, "list", "url-path");
		
		
		if (!this.isWebProject())
		{
			removeChildren(pathsDOM);
			return;
		}

		//
		// <url path="[relative-path-to-source-root]" />
		//
		Xpp3Dom targetDOM = new Xpp3Dom("list");
		for (Iterator i = sourceRoots.iterator(); i.hasNext();) {
			File sourceRoot = new File((String) i.next());
			String relativeRoot = getRelativeDir(projectDir, sourceRoot);
			Xpp3Dom urlDOM = new Xpp3Dom("url");
			urlDOM.setAttribute("path", relativeRoot);
			targetDOM.addChild(urlDOM);
		}

		// TODO: use a better merge algorithm
		removeChildren(sourceDOM);

		// make sure to pass Boolean.FALSE to allow
		// multiple child elements with the same name
		Xpp3Dom.mergeXpp3Dom(sourceDOM, targetDOM, Boolean.FALSE);
	}

	private void replaceWebappInfo(String projectName, Xpp3Dom projectDOM) throws XmlPullParserException {
			
			// /jpr:project
			// /hash[@n="oracle.jdeveloper.model.J2eeSettings"]
			// /value[@n="j2eeWebAppName" v="maven-generated-webapp"]
			// /value[@n="j2eeWebContextRoot" v="maven-generated-context-root"]
			Xpp3Dom settingsDOM = findNamedChild(projectDOM, "hash",
					"oracle.jdeveloper.model.J2eeSettings");
			Xpp3Dom webappNameDOM = findNamedChild(settingsDOM, "value",
					"j2eeWebAppName");
			Xpp3Dom webappContextDOM = findNamedChild(settingsDOM, "value",
					"j2eeWebContextRoot");
			
			if (!this.isWebProject())
			{
				removeChildren(settingsDOM);
				return;
			}
	
			String contextName = this.project.getProperties().getProperty("webApp.context");
	
			webappNameDOM.setAttribute("v", projectName);
			if (contextName != null) {
				webappContextDOM.setAttribute("v", contextName);
			} else {
				// update the webapp context root
				webappContextDOM.setAttribute("v", projectName);
			}
		
	}

	private void replaceSourcePaths(File projectDir, List sourceRoots,
			List resourceRoots, Xpp3Dom projectDOM)
			throws XmlPullParserException {
		// /jpr:project
		// /hash[@n="oracle.jdeveloper.model.PathsConfiguration"]
		// /hash[@n="javaContentSet"]
		// /list[@n="url-path"]
		Xpp3Dom pathsDOM = findNamedChild(projectDOM, "hash",
				"oracle.jdeveloper.model.PathsConfiguration");
		Xpp3Dom contentSetDOM = findNamedChild(pathsDOM, "hash",
				"javaContentSet");
		Xpp3Dom sourceDOM = findNamedChild(contentSetDOM, "list",
				"constituent-sets");

		//
		// <url path="[relative-path-to-source-root]" />
		//
		Xpp3Dom targetDOM = new Xpp3Dom("list");

		Collections.sort(sourceRoots);
		for (Iterator i = sourceRoots.iterator(); i.hasNext();) {
			File sourceRoot = new File((String) i.next());
			Xpp3Dom hashDOM = new Xpp3Dom("hash");

			Xpp3Dom listDOM = new Xpp3Dom("list");
			listDOM.setAttribute("n", "pattern-filters");
			hashDOM.addChild(listDOM);

			Xpp3Dom stringDOM = new Xpp3Dom("string");
			stringDOM.setAttribute("v", "+**");
			listDOM.addChild(stringDOM);

			listDOM = new Xpp3Dom("list");
			listDOM.setAttribute("n", "url-path");
			hashDOM.addChild(listDOM);

			String relativeRoot = getRelativeDir(projectDir, sourceRoot);
			Xpp3Dom urlDOM = new Xpp3Dom("url");
			urlDOM.setAttribute("path", relativeRoot);
			listDOM.addChild(urlDOM);

			targetDOM.addChild(hashDOM);
		}

		// TODO: get bug fixed in 10.1.3 for copying resources
		Collections.sort(resourceRoots, new Comparator() {
			public int compare(Object a, Object b) {
				Resource ra = (Resource) a;
				Resource rb = (Resource) b;
				return ra.getDirectory().compareTo(rb.getDirectory());
			}

		});

		for (Iterator i = resourceRoots.iterator(); i.hasNext();) {
			Resource resource = (Resource) i.next();
			File resourceRoot = new File(resource.getDirectory());
			String relativeRoot = getRelativeDir(projectDir, resourceRoot);

			Xpp3Dom hashDOM = new Xpp3Dom("hash");

			Xpp3Dom listDOM = new Xpp3Dom("list");
			listDOM.setAttribute("n", "pattern-filters");
			hashDOM.addChild(listDOM);

			Xpp3Dom stringDOM = null;
			// TODO: This is a Hack for excluding the golden files,
			// which are not really xml files. We need a way, in
			// the pom file to specify excludes.
			if (relativeRoot.startsWith("src/test/resources")) {
				stringDOM = new Xpp3Dom("string");
				stringDOM.setAttribute("v",
						"-oracle/adfinternal/view/faces/renderkit/golden/");
				listDOM.addChild(stringDOM);
			}

			stringDOM = new Xpp3Dom("string");
			stringDOM.setAttribute("v", "+**");
			listDOM.addChild(stringDOM);

			listDOM = new Xpp3Dom("list");
			listDOM.setAttribute("n", "url-path");
			hashDOM.addChild(listDOM);

			Xpp3Dom urlDOM = new Xpp3Dom("url");
			urlDOM.setAttribute("path", relativeRoot);
			listDOM.addChild(urlDOM);

			targetDOM.addChild(hashDOM);
		}

		// TODO: use a better merge algorithm
		removeChildren(sourceDOM);

		// make sure to pass Boolean.FALSE to allow
		// multiple child elements with the same name
		Xpp3Dom.mergeXpp3Dom(sourceDOM, targetDOM, Boolean.FALSE);
	}
	
	private Xpp3Dom addResource(String name){
		
		Xpp3Dom hash = new Xpp3Dom("hash");

		Xpp3Dom list = new Xpp3Dom("list");
		list.setAttribute("n", "pattern-filters");
		hash.addChild(list);

		Xpp3Dom string = new Xpp3Dom("string");
		string.setAttribute("v", "+*");
		list.addChild(string);

		list = new Xpp3Dom("list");
		list.setAttribute("n", "url-path");
		hash.addChild(list);

		Xpp3Dom url = new Xpp3Dom("url");
		url.setAttribute("path", name);
		list.addChild(url);
		
		return hash;
	}
	
	private void replaceResourcePaths(File projectDir, List resourceRoots,
			Xpp3Dom projectDOM) throws XmlPullParserException {
		// /jpr:project
		// /hash[@n="oracle.ide.model.ResourcePaths"]
		// /hash[@n="resourcesContentSet"]
		// /list[@n="url-path"]
		
		
		Xpp3Dom pathsDOM = findNamedChild(projectDOM, "hash", "oracle.ide.model.ResourcePaths");
		Xpp3Dom contentSetDOM = findNamedChild(pathsDOM, "hash", "resourcesContentSet");
		Xpp3Dom sourceDOM = findNamedChild(contentSetDOM, "list", "constituent-sets");

		//
		// <url path="[relative-path-to-source-root]" />
		//
		Xpp3Dom targetDOM = new Xpp3Dom("list");
		
		targetDOM.addChild(addResource("."));
		// add pom.xml as resource
		targetDOM.addChild(addResource("pom.xml"));
		
		for (Iterator i = resourceRoots.iterator(); i.hasNext();) {
			
			Resource resource = (Resource) i.next();
			File resourceRoot = new File(resource.getDirectory());
			Xpp3Dom hashDOM = new Xpp3Dom("hash");

			Xpp3Dom listDOM = new Xpp3Dom("list");
			listDOM.setAttribute("n", "pattern-filters");
			hashDOM.addChild(listDOM);

			Xpp3Dom stringDOM = new Xpp3Dom("string");
			stringDOM.setAttribute("v", "+**");
			listDOM.addChild(stringDOM);

			listDOM = new Xpp3Dom("list");
			listDOM.setAttribute("n", "url-path");
			hashDOM.addChild(listDOM);

			String relativeRoot = getRelativeDir(projectDir, resourceRoot);
			Xpp3Dom urlDOM = new Xpp3Dom("url");
			urlDOM.setAttribute("path", relativeRoot);
			listDOM.addChild(urlDOM);

			targetDOM.addChild(hashDOM);						
		}

		// TODO: use a better merge algorithm
		removeChildren(sourceDOM);

		// make sure to pass Boolean.FALSE to allow
		// multiple child elements with the same name
		Xpp3Dom.mergeXpp3Dom(sourceDOM, targetDOM, Boolean.FALSE);
	}

	private void replaceDependencies(File projectDir, List dependencies,
			Xpp3Dom projectDOM) throws XmlPullParserException {
		// /jpr:project
		// /hash[@n="oracle.ide.model.DependencyConfiguration"]
		// /list[@n="dependencyList"]
		Xpp3Dom configDOM = findNamedChild(projectDOM, "hash",
				"oracle.ide.model.DependencyConfiguration");
		Xpp3Dom sourceDOM = findNamedChild(configDOM, "list", "dependencyList");

		Xpp3Dom targetDOM = new Xpp3Dom("list");
		for (Iterator i = dependencies.iterator(); i.hasNext();) {
			Dependency dependency = (Dependency) i.next();
			MavenProject dependentProject = findDependentProject(dependency
					.getManagementKey());
			if (dependentProject != null) {
				File dependentProjectFile = getJProjectFile(dependentProject);
				String relativePath = getRelativeFile(projectDir,
						dependentProjectFile);

				Xpp3Dom hashDOM = new Xpp3Dom("hash");
				Xpp3Dom valueDOM = new Xpp3Dom("value");
				valueDOM.setAttribute("n", "class");
				valueDOM.setAttribute("v",
						"oracle.jdeveloper.library.ProjectLibrary");
				Xpp3Dom srcOwnValDOM = new Xpp3Dom("value");
				srcOwnValDOM.setAttribute("n", "sourceOwnerURL");
				Xpp3Dom urlDOM = new Xpp3Dom("url");
				urlDOM.setAttribute("n", "sourceURL");
				urlDOM.setAttribute("path", relativePath);
				hashDOM.addChild(valueDOM);
				hashDOM.addChild(srcOwnValDOM);
				hashDOM.addChild(urlDOM);
				targetDOM.addChild(hashDOM);
			}
		}

		// TODO: use a better merge algorithm
		removeChildren(sourceDOM);

		// make sure to pass Boolean.FALSE to allow
		// multiple child elements with the same name
		Xpp3Dom.mergeXpp3Dom(sourceDOM, targetDOM, Boolean.FALSE);
	}

	private void replaceLibraries(File projectDir, List artifacts,
			Xpp3Dom projectDOM) throws XmlPullParserException {
		// /jpr:project
		// /hash[@n="oracle.jdevimpl.config.JProjectLibraries"]
		// /hash[@n="internalDefinitions"]
		// /list[@n="libraryDefinitions"]
		Xpp3Dom projectLibsDOM = findNamedChild(projectDOM, "hash",
				"oracle.jdevimpl.config.JProjectLibraries");
		Xpp3Dom internalDefsDOM = findNamedChild(projectLibsDOM, "hash",
				"internalDefinitions");
		Xpp3Dom sourceDefsDOM = findNamedChild(internalDefsDOM, "list",
				"libraryDefinitions");

		// /jpr:project
		// /hash[@n="oracle.jdevimpl.config.JProjectLibraries"]
		// /list[@n="libraryReferences"]
		Xpp3Dom sourceRefsDOM = findNamedChild(projectLibsDOM, "list",
				"libraryReferences");

		Xpp3Dom targetDefsDOM = new Xpp3Dom("list");
		Xpp3Dom targetRefsDOM = new Xpp3Dom("list");

		//
		// libraryDefinitions
		//
		// <hash>
		// <list n="classPath">
		// <url path="[path-to-artifact]" jar-entry="" />
		// </list>
		// <value n="deployedByDefault" v="true"/>
		// <value n="description" v="[artifact.id]"/>
		// <value n="id" v="[artifact.id]"/>
		// </hash>
		//

		// sort the artifacts
		Collections.sort(artifacts, new Comparator() {
			public int compare(Object a, Object b) {
				Artifact arta = (Artifact) a;
				Artifact artb = (Artifact) b;
				return arta.getId().compareTo(artb.getId());
			}

		});

		List libraryRefs = new LinkedList();
		for (Iterator i = artifacts.iterator(); i.hasNext();) {
			Artifact artifact = (Artifact) i.next();
			
				String id = artifact.getId();
				String path = getRelativeFile(projectDir, artifact.getFile());

				// libraryDefinitions entry
				Xpp3Dom hashDOM = new Xpp3Dom("hash");
				Xpp3Dom listDOM = new Xpp3Dom("list");
				listDOM.setAttribute("n", "classPath");

				Xpp3Dom urlDOM = new Xpp3Dom("url");
				urlDOM.setAttribute("path", path);
				urlDOM.setAttribute("jar-entry", "");
				listDOM.addChild(urlDOM);
				hashDOM.addChild(listDOM);

				Xpp3Dom valueDOM = new Xpp3Dom("value");
				valueDOM.setAttribute("n", "deployedByDefault");
				valueDOM.setAttribute("v", String.valueOf("compile".equalsIgnoreCase(artifact.getScope())));
				hashDOM.addChild(valueDOM);

				valueDOM = new Xpp3Dom("value");
				valueDOM.setAttribute("n", "description");
				valueDOM.setAttribute("v", id);
				hashDOM.addChild(valueDOM);

				valueDOM = new Xpp3Dom("value");
				valueDOM.setAttribute("n", "id");
				valueDOM.setAttribute("v", id);
				hashDOM.addChild(valueDOM);
				targetDefsDOM.addChild(hashDOM);
				
				/*<list n="docPath">
                <url path="../../../../Flexvision2Lib/flexvision2_m/target/flexvision-m-2.0.0-sources.jar" jar-entry=""/>
                </list>
                <value n="id" v="com.logus.flexvision:flexvision-m:jar:2.0.0"/>
                <list n="sourcePath">
                   <url path="../../../../Flexvision2Lib/flexvision2_m/target/flexvision-m-2.0.0-sources.jar" jar-entry=""/>
                </list>*/
				
				if (this.downloadSources)
				{
					File sourceArtifact = this.resolveDependenciesWithClassifier(artifact, "sources", true);
					if (sourceArtifact != null)
					{
						Xpp3Dom listSourcePath = new Xpp3Dom("list");
						listSourcePath.setAttribute("n", "sourcePath");
						
						Xpp3Dom urlSourcePath = new Xpp3Dom("url");
						urlSourcePath.setAttribute("path", Files.getRelativeFile(projectDir, sourceArtifact));
						urlSourcePath.setAttribute("jar-entry", "");
						listSourcePath.addChild(urlSourcePath);						
						
						hashDOM.addChild(listSourcePath);
						
						/*//<value n="id" v="com.logus.flexvision:flexvision-vc-rich-dec:jar:2.0.0"/>
						Xpp3Dom descritpion = new Xpp3Dom("value");
						descritpion.setAttribute("n", "id");
						descritpion.setAttribute("v", id);						
						hashDOM.addChild(descritpion);*/
					}
				}
				
				if (this.downloadJavadocs)
				{
					File javadocArtifact = this.resolveDependenciesWithClassifier(artifact, "javadoc", true);
					if (javadocArtifact != null) {
						Xpp3Dom listJavadocPath = new Xpp3Dom("list");
						listJavadocPath.setAttribute("n", "docPath");

						Xpp3Dom urlJavadocPath = new Xpp3Dom("url");
						urlJavadocPath.setAttribute("path", Files.getRelativeFile(projectDir, javadocArtifact));
						urlJavadocPath.setAttribute("jar-entry", "");
						listJavadocPath.addChild(urlJavadocPath);						
						hashDOM.addChild(listJavadocPath);
						
						/*if (!downloadSources) {
							// <value n="id" v="com.logus.flexvision:flexvision-vc-rich-dec:jar:2.0.0"/>
							Xpp3Dom descritpion = new Xpp3Dom("value");
							descritpion.setAttribute("n", "id");
							descritpion.setAttribute("v", id);							
							hashDOM.addChild(descritpion);
						}*/
					}
				}
				
				libraryRefs.add(id);
			
		}

		// This boolean is set by specifying the jdev.plugin.add.libraries
		// property at the top of a project's pom file. There are projects
		// that don't need these libraries in their .jpr files to compile and
		// this
		// property allows them to be excluded.
		//
		// IMPORTANT NOTE: if this property is set in a project,
		// then libraries will NOT be added, even though they
		// may be specified under the maven-jdev-plugin.
		if (_addLibraries) {
			// Add libraries. The default libraries can be
			// overridden in the trunk/toplevel pom file.
			if (_releaseMajor >= 11) {
				// TODO First check JDEV version
				// Add the default libraries
				libraryRefs.add(0, "JSF 2.0");
				libraryRefs.add(0, "JSP Runtime");
				
				//libraryRefs.add(0, "JSTL 1.2");
				//libraryRefs.add(0, "JSTL 1.2 Tags");
				
			}

			// Add the libraries
			if (libraries != null) {
				getLog().info("Adding the JDeveloper Libraries");
				for (int i = 0; i < libraries.length; i++) {
					// Default libraries for release 11 were already
					// added above.
					if ((_releaseMajor >= 11) && (("JSF 1.2".equalsIgnoreCase(libraries[i])) || ("JSP Runtime".equalsIgnoreCase(libraries[i])))) {
						continue;
					}
					// Add the library
					libraryRefs.add(0, libraries[i]);
					getLog().info("Added the library " + libraries[i]);
				}
			}
		}

		//
		// libraryReferences
		//
		// <hash>
		// <value n="id" v="[artifact.id]"/>
		// <value n="isJDK" v="false"/>
		// </hash>
		//
		Collections.sort(libraryRefs);

		for (Iterator i = libraryRefs.iterator(); i.hasNext();) {
			String id = (String) i.next();

			// libraryReferences entry
			Xpp3Dom hashDOM = new Xpp3Dom("hash");
			Xpp3Dom valueDOM = new Xpp3Dom("value");
			valueDOM.setAttribute("n", "id");
			valueDOM.setAttribute("v", id);
			hashDOM.addChild(valueDOM);

			valueDOM = new Xpp3Dom("value");
			valueDOM.setAttribute("n", "isJDK");
			valueDOM.setAttribute("v", "false");
			hashDOM.addChild(valueDOM);
			targetRefsDOM.addChild(hashDOM);
		}

		// First, add JSP Runtime dependency if src/main/webapp exists
		// TODO: use a better merge algorithm
		removeChildren(sourceDefsDOM);
		removeChildren(sourceRefsDOM);

		// make sure to pass Boolean.FALSE to allow
		// multiple child elements with the same name
		Xpp3Dom.mergeXpp3Dom(sourceDefsDOM, targetDefsDOM, Boolean.FALSE);
		Xpp3Dom.mergeXpp3Dom(sourceRefsDOM, targetRefsDOM, Boolean.FALSE);
	}

	private File resolveDependenciesWithClassifier(final Artifact dependency, final String inClassifier, boolean includeRemoteRepositories) {
		
		 // if downloadSources is off, just check
        // local repository for reporting missing source jars
        List remoteRepos = this.remoteArtifactRepositories  != null ? this.remoteArtifactRepositories : Collections.EMPTY_LIST;
        
        if (this.getLog().isDebugEnabled())
        {
        	getLog().debug(
                     "Searching for " + inClassifier + " for " + dependency.getId() + ":" + dependency.getClassifier() + " at " + dependency.getId() + ":" + inClassifier );
        }
			
			Artifact artifact =
                IdeUtils.createArtifactWithClassifier( dependency.getGroupId(), dependency.getArtifactId(),
                		dependency.getVersion(), null, inClassifier, artifactFactory );
			
			File notAvailableMarkerFile = IdeUtils.getNotAvailableMarkerFile( localRepository, artifact );

            if ( force && notAvailableMarkerFile.exists() )
            {
                if ( !notAvailableMarkerFile.delete() )
                {
                    getLog().warn(Messages.getString("AbstractIdeSupportMojo.unabletodeletenotavailablemarkerfile", notAvailableMarkerFile ) );
                }
            }
			
            if ( !notAvailableMarkerFile.exists() )
            {
            	artifact =
                    IdeUtils.resolveArtifact( artifactResolver, artifact, remoteRepos, localRepository, getLog() );
                if ( artifact.isResolved() )
                {
                    if ( "sources".equals( inClassifier ) )
                    {
                        //dependency.setSourceAttachment( artifact.getFile() );
                    	this.getLog().info("Source File: " + artifact.getFile());
                    	return artifact.getFile();
                    }
                    else if ( "javadoc".equals( inClassifier ) )
                    {
                        //dependency.setJavadocAttachment( artifact.getFile() );
                    	return artifact.getFile();
                    }
                }
                else
                {
                    if ( includeRemoteRepositories )
                    {
                        try
                        {
                            notAvailableMarkerFile.createNewFile();
                            getLog().debug(
                                            Messages.getString( "AbstractIdeSupportMojo.creatednotavailablemarkerfile",
                                                                notAvailableMarkerFile ) );
                        }
                        catch ( IOException e )
                        {
                            getLog().warn(
                                           Messages.getString(
                                                               "AbstractIdeSupportMojo.failedtocreatenotavailablemarkerfile",
                                                               notAvailableMarkerFile ) );
                        }
                    }
                    // add the dependencies to the list
                    // of those lacking the required
                    // artifact
                    //missingClassifierDependencies.add( sourceArtifact );                    
                    if (this.getLog().isDebugEnabled())
                    {
                    	getLog().debug(
                                 "Missing " + inClassifier + " for " + dependency.getId() + ":" + dependency.getClassifier() + " at " + dependency.getId() + ":" + inClassifier );
                    }
                }
            }
            
            return null;
	}

	private void replaceLocalTagLibraries(Xpp3Dom targetLibsDOM)
			throws XmlPullParserException {
		TldContentHandler tldHandler = new TldContentHandler();
		String path = null;

		// Loop through all the .tld files in the WEB-INF dir
		// Parse each and get the values needed for the TagLibraries
		// hash in the .jpr file.
		try {
			File webInfDir = new File(project.getBasedir(), "src/main/webapp/WEB-INF");
			
			File[] files = webInfDir.listFiles();
			
			if (files == null)
				return;

			Xpp3Dom hashDOM = null;
			Xpp3Dom valueDOM = null;
			for (int i = 0; i < files.length; i++) {
				path = files[i].getPath();
				if (path.endsWith(".tld")) {
					hashDOM = new Xpp3Dom("hash");
					// we have a tag library. Parse it and
					// get the values needed for the .jpr file

					// Parse
					tldHandler.parseTld(files[i]);
					// call gettors to get the values
					valueDOM = new Xpp3Dom("value");
					valueDOM.setAttribute("n", "jspVersion");
					valueDOM.setAttribute("v", tldHandler.getJspVersion());
					hashDOM.addChild(valueDOM);

					valueDOM = new Xpp3Dom("value");
					valueDOM.setAttribute("n", "name");
					valueDOM.setAttribute("v", tldHandler.getName());
					hashDOM.addChild(valueDOM);

					valueDOM = new Xpp3Dom("value");
					valueDOM.setAttribute("n", "prefix");
					valueDOM.setAttribute("v", tldHandler.getPrefix());
					hashDOM.addChild(valueDOM);

					valueDOM = new Xpp3Dom("value");
					valueDOM.setAttribute("n", "tldURL");
					valueDOM.setAttribute(
							"v",
							"WEB-INF/"
									+ path.substring(path.indexOf("WEB-INF") + 8));
					hashDOM.addChild(valueDOM);

					valueDOM = new Xpp3Dom("value");
					valueDOM.setAttribute("n", "URI");
					valueDOM.setAttribute("v", tldHandler.getURI());
					hashDOM.addChild(valueDOM);

					valueDOM = new Xpp3Dom("value");
					valueDOM.setAttribute("n", "version");
					valueDOM.setAttribute("v", tldHandler.getVersion());
					hashDOM.addChild(valueDOM);

					// Add each file's hash to the tag-libraries "list"
					targetLibsDOM.addChild(hashDOM);
				} // endif
			} // endfor
			return;
		} catch (SAXException saxex) {
			getLog().info(
					"SAX Parse Exception parsing " + path + ": "
							+ saxex.getMessage(), saxex);
		} catch (IOException ioe) {
			getLog().info("Unable to open an InputStream to " + path, ioe);
		} catch (ParserConfigurationException pce) {
			getLog().info("Unable to create SAX parser for " + path, pce);
		}
	}

	private void replaceTagLibraries(Xpp3Dom projectDOM, File projectDir, List dependencies, List artifacts) throws XmlPullParserException {
		
		// This boolean is set by specifying the jdev.plugin.add.taglibs
		// property at the top of a project's pom file. There are projects
		// that don't need taglibs in their .jpr files to compile and this
		// allows them to be excluded.
		if (!_addTagLibs)
			return;

		// /jpr:project
		// /hash[@n="oracle.jdevimpl.webapp.jsp.libraries.model.ProjectTagLibraries"]
		// /list[@n="tag-libaries"]
		Xpp3Dom projectTagsDOM = findNamedChild(projectDOM, "hash", "oracle.jdevimpl.webapp.jsp.libraries.model.ProjectTagLibraries");
		Xpp3Dom tagLibsDOM = findNamedChild(projectTagsDOM, "list", "tag-libraries");

		Xpp3Dom targetLibsDOM = new Xpp3Dom("list");

		//
		// tagLibraryDefinitions
		//
		// <hash>
		// <hash n="baseLibrary">
		// <value n="name" v="JSF HTML"></value>
		// <value n="version" v="1.2"></value>
		// </hash>
		// <value n="jspVersion" v="2.1"></value>
		// <value n="name" v="JSF HTML"></value>
		//
		// <value n="tldURL"
		// v="@oracle.home@/jdeveloper/modules/oracle.jsf_1.2.9/glassfish.jsf_1.2.9.0.jar!/META-INF"></value>
		// <value n="URI" v="http://java.sun.com/jsf/html"></value>
		// <value n="version" v="1.2"></value>
		// </hash>
		//

		// Parent "hash"
		Xpp3Dom hashDOM = null;

		// <value...
		Xpp3Dom valueDOM = null;

		if (distributedTagLibraries != null && distributedTagLibraries.length > 0) {
			// Process each distributed Tag Library
			for (int i = 0; i < distributedTagLibraries.length; i++) {
				Properties disTagLib = distributedTagLibraries[i];
				String nameName = null;
				String nameValue = null;
				String versionName = null;
				String versionValue = null;

				// Create parent hash for each taglib
				hashDOM = new Xpp3Dom("hash");

				// baseLibrary "hash" for each taglib
				Xpp3Dom hashBaseDOM = new Xpp3Dom("hash");
				hashBaseDOM.setAttribute("n", "baseLibrary");
				// Add baseLibrary hash to parent hash
				hashDOM.addChild(hashBaseDOM);

				// Process each property of the taglib
				for (Enumeration keys = disTagLib.propertyNames(); keys
						.hasMoreElements();) {
					// Get the name value pair
					String name = (String) keys.nextElement();
					String value = (String) disTagLib.get(name);

					// Put the name and version values
					// inside the baseLibrary hash.
					// This only happens once per taglib
					if ("name".equals(name)) {
						nameName = name;
						nameValue = value;

						// n="name, v=<name of taglib> in baseLibrary
						valueDOM = new Xpp3Dom("value");
						valueDOM.setAttribute("n", name);
						valueDOM.setAttribute("v", value);
						hashBaseDOM.addChild(valueDOM);

						// Duplicate the "name" <value...
						// outside of the baseLibrary
						// n="name, v=<name of taglib> in parent hash
						valueDOM = new Xpp3Dom("value");
						valueDOM.setAttribute("n", nameName);
						valueDOM.setAttribute("v", nameValue);
						hashDOM.addChild(valueDOM);
					} else if ("version".equals(name)) {
						versionName = name;
						versionValue = value;

						// n="version" v=<taglib version> in baseLibrary
						valueDOM = new Xpp3Dom("value");
						valueDOM.setAttribute("n", name);
						valueDOM.setAttribute("v", value);
						hashBaseDOM.addChild(valueDOM);

						// Duplicate the "version" <value...
						// outside of the baseLibrary
						// n="version" v=<taglib version> in parent hash
						valueDOM = new Xpp3Dom("value");
						valueDOM.setAttribute("n", versionName);
						valueDOM.setAttribute("v", versionValue);
						hashDOM.addChild(valueDOM);
					} else {
						if ("tld".equals(name)) {
							// Did not want to have a URL in the pom file.
							// I just wanted the user to specify the name
							// of the tld file. So we fix it for JDev
							// here.
							name += "URL";
							value = tagLibDirectory + "/" + value;
						}
						valueDOM = new Xpp3Dom("value");
						valueDOM.setAttribute("n", name);
						valueDOM.setAttribute("v", value);
						hashDOM.addChild(valueDOM);
					}
				} // endfor processing each property

				// We are done with this disTagLib
				// Add it to the targetLibsDOM
				if (hashDOM != null)
					targetLibsDOM.addChild(hashDOM);
			} // endfor processing each distributed tag lib
		} // endif
		else {
			// Use Default tag library configuration. See comment before
			// replaceTagLibraries.
			replaceDefaultTagLibraries(projectDOM, targetLibsDOM);
			this.addTagLibrariesOfDependencies(projectDir, dependencies, artifacts, projectDOM, targetLibsDOM);
		}

		// Generate .jpr entries from the .tld files in the project's
		// src/main/webapp/WEB-INF directory.
		replaceLocalTagLibraries(targetLibsDOM);

		// First, add JSP Runtime dependency if src/main/webapp exists
		// TODO: use a better merge algorithm
		removeChildren(tagLibsDOM);

		// make sure to pass Boolean.FALSE to allow
		// multiple child elements with the same name
		Xpp3Dom.mergeXpp3Dom(tagLibsDOM, targetLibsDOM, Boolean.FALSE);
	}

	private void replaceDefaultTagLibraries(Xpp3Dom projectDOM, Xpp3Dom targetLibsDOM) throws XmlPullParserException {
		
		/*// Begin JSF HTML Tag lib
		Xpp3Dom hashDOM = new Xpp3Dom("hash");
		Xpp3Dom hashBaseDOM = new Xpp3Dom("hash");
		Xpp3Dom valueDOM = null;

		hashBaseDOM.setAttribute("n", "baseLibrary");

		// Add baseLibrary hash to parent hash
		hashDOM.addChild(hashBaseDOM);

		// Create parent hash for each taglib
		// hashDOM = new Xpp3Dom("hash");

		// baseLibrary "hash" for each taglib
		// Xpp3Dom hashBaseDOM = new Xpp3Dom("hash");
		// hashBaseDOM.setAttribute("n", "baseLibrary");

		// n="name, v=<name of taglib> in baseLibrary
		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "name");
		valueDOM.setAttribute("v", "JSF HTML");
		hashBaseDOM.addChild(valueDOM);

		// Duplicate the "name" <value...
		// outside of the baseLibrary
		// n="name, v=<name of taglib> in parent hash
		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "name");
		valueDOM.setAttribute("v", "JSF HTML");
		hashDOM.addChild(valueDOM);

		// n="version" v=<taglib version> in baseLibrary
		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "version");
		valueDOM.setAttribute("v", "1.2");
		hashBaseDOM.addChild(valueDOM);

		// Duplicate the "version" <value...
		// outside of the baseLibrary
		// n="name, v=<name of taglib> in parent hash
		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "version");
		valueDOM.setAttribute("v", "1.2");
		hashDOM.addChild(valueDOM);

		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "jspVersion");
		valueDOM.setAttribute("v", "2.1");
		hashDOM.addChild(valueDOM);

		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "tldURL");
		valueDOM.setAttribute("v", tagLibDirectory + "/html_basic.tld");
		hashDOM.addChild(valueDOM);

		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "URI");
		valueDOM.setAttribute("v", "http://java.sun.com/jsf/html");
		hashDOM.addChild(valueDOM);

		// ADF LIbraries

		// We are done with this disTagLib
		// Add it to the targetLibsDOM
		if (hashDOM != null)
			targetLibsDOM.addChild(hashDOM);

		// Begin JSF Core Taglib
		hashDOM = new Xpp3Dom("hash");
		hashBaseDOM = new Xpp3Dom("hash");

		hashBaseDOM.setAttribute("n", "baseLibrary");

		// Add baseLibrary hash to parent hash
		hashDOM.addChild(hashBaseDOM);

		// n="name, v=<name of taglib> in baseLibrary
		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "name");
		valueDOM.setAttribute("v", "JSF Core");
		hashBaseDOM.addChild(valueDOM);

		// Duplicate the "name" <value...
		// outside of the baseLibrary
		// n="name, v=<name of taglib> in parent hash
		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "name");
		valueDOM.setAttribute("v", "JSF Core");
		hashDOM.addChild(valueDOM);

		// n="version" v=<taglib version> in baseLibrary
		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "version");
		valueDOM.setAttribute("v", "1.2");
		hashBaseDOM.addChild(valueDOM);

		// Duplicate the "version" <value...
		// outside of the baseLibrary
		// n="name, v=<name of taglib> in parent hash
		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "version");
		valueDOM.setAttribute("v", "1.2");
		hashDOM.addChild(valueDOM);

		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "jspVersion");
		valueDOM.setAttribute("v", "2.1");
		hashDOM.addChild(valueDOM);

		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "tldURL");
		valueDOM.setAttribute("v", tagLibDirectory + "/jsf_core.tld");
		hashDOM.addChild(valueDOM);

		valueDOM = new Xpp3Dom("value");
		valueDOM.setAttribute("n", "URI");
		valueDOM.setAttribute("v", "http://java.sun.com/jsf/core");
		hashDOM.addChild(valueDOM);

		// We are done with this disTagLib
		// Add it to the targetLibsDOM
		if (hashDOM != null)
			targetLibsDOM.addChild(hashDOM);*/
		
		this.replaceADFDefaultTagLibraries(projectDOM, targetLibsDOM);
	}
	
	private void replaceADFDefaultTagLibraries(Xpp3Dom projectDOM, Xpp3Dom targetLibsDOM) throws XmlPullParserException {
		
		String[][] libraries = { 
				
				{"ADF Data Visualization", "1.1", "2.1", "@oracle.home@../oracle_common/modules/oracle.adf.view_11.1.1/dvt-faces.jar!/META-INF/bi.tld",
				 "http://xmlns.oracle.com/dss/adf/faces"	
				}, 
				
				{
					"ADF Faces Components", "11", "1.2",  "@oracle.home@../oracle_common/modules/oracle.adf.view_11.1.1/adf-richclient-impl-11.jar!/META-INF/afu.tld",
					"http://xmlns.oracle.com/adf/faces/rich"
				},
				
				{
					"JSTL Functions", "1.2", "2.1", "@oracle.home@../modules/glassfish.jstl_1.2.0.1.jar!/META-INF/fn.tld", "http://java.sun.com/jsp/jstl/functions"
				},
				
				{
					"JSTL Permitted Taglibs", "1.2", "2.1", "@oracle.home@../modules/glassfish.jstl_1.2.0.1.jar!/META-INF/permittedTaglibs.tld",
					"http://jakarta.apache.org/taglibs/standard/permittedTaglibs"
				},
				
				{
					"JSTL SQL", "1.2", "2.1", "@oracle.home@../modules/glassfish.jstl_1.2.0.1.jar!/META-INF/sql.tld", "http://java.sun.com/jsp/jstl/sql"
				},
				
				{
					"JSTL Core", "1.2", "2.1", "@oracle.home@../modules/glassfish.jstl_1.2.0.1.jar!/META-INF/c.tld", "http://java.sun.com/jsp/jstl/core"
				},
				
				{
					"JSTL Format", "1.2", "2.1", "@oracle.home@../modules/glassfish.jstl_1.2.0.1.jar!/META-INF/fmt.tld", "http://java.sun.com/jsp/jstl/fmt"
				},
				
				{
					"JSTL Script Free", "1.2", "2.1", "@oracle.home@../modules/glassfish.jstl_1.2.0.1.jar!/META-INF/scriptfree.tld", "http://jakarta.apache.org/taglibs/standard/scriptfree"
				},
				
				{
					"JSTL XML", "1.2", "2.1", "@oracle.home@../modules/glassfish.jstl_1.2.0.1.jar!/META-INF/x.tld", "http://java.sun.com/jsp/jstl/xml"
				},
				
				{
					"Trinidad HTML Components", "2.0", "1.2", "@oracle.home@../oracle_common/modules/oracle.adf.view_11.1.1/trinidad-impl.jar!/META-INF/trh.tld",
					"http://myfaces.apache.org/trinidad/html"
				},
				
				{
					"Trinidad Components", "2.0", "1.2", "@oracle.home@../oracle_common/modules/oracle.adf.view_11.1.1/trinidad-impl.jar!/META-INF/tr.tld",
					"http://myfaces.apache.org/trinidad"
				},
				{
					"JSF Core", "2.0", "2.1", "@oracle.home@../oracle_common/modules/oracle.jsf_2.0/jsf-impl.jar!/META-INF/jsf_core.tld",
					"http://java.sun.com/jsf/core"
				},
				
				{
					"JSF HTML", "2.0", "2.1", "@oracle.home@../oracle_common/modules/oracle.jsf_2.0/jsf-impl.jar!/META-INF/html_basic.tld",
					"http://java.sun.com/jsf/html"
				}				
		};
		
		this.addTagLibraries(libraries, projectDOM, targetLibsDOM);
	}
	
	private void addTagLibrariesOfDependencies(File projectDir, List dependencies, List artifacts, Xpp3Dom projectDOM, Xpp3Dom targetLibsDOM) throws XmlPullParserException 
	{
		try {
			String[][] libraries = this.getTaglibrariesOfDependencies(projectDir, dependencies, artifacts);
			this.addTagLibraries(libraries, projectDOM, targetLibsDOM);
		} catch (SAXException e) {
			this.getLog().error(e);
		} catch (ParserConfigurationException e) {
			this.getLog().error(e);
		}catch(IOException exception){
			this.getLog().error(exception);
		}
	}
	
	private String[][] getTaglibrariesOfDependencies(File projectDir, List dependencies, List<Artifact> artifacts) throws IOException, SAXException, ParserConfigurationException
	{
	
		Map<String,List<File>> sourceMap = new TreeMap<String, List<File>>();
		
		for (Iterator<Artifact> i = artifacts.iterator(); i.hasNext();) {
			Artifact artifact = i.next();
			
			String separator;
			
			if (File.separatorChar == '/')
				separator = File.separator;
			else
				separator = File.separator + File.separator;
			
			
			List<String> tagLibrariesPackages = new ArrayList<String>();
			if (this.includeTagLibrariesOfPackage != null)
			{
				tagLibrariesPackages.add(this.includeTagLibrariesOfPackage.replaceAll("\\.", separator));				
			}
			
			if (this.includeTagLibrariesOfPackages != null)
			{
			  for(String domain : this.includeTagLibrariesOfPackages) 
			  {				 
				  tagLibrariesPackages.add(domain.replaceAll("\\.", separator));
			  }
			} 
			
			if (!tagLibrariesPackages.isEmpty()) 
			{			
				/*this.getLog().info("TagLib Package " + this.includeTagLibrariesOfPackage + artifact.getFile().getAbsolutePath() + " " + 
						artifact.getFile().getAbsolutePath().contains(this.includeTagLibrariesOfPackage.replaceAll("\\.", separator))); */
				
				if (!isDependentProject(artifact.getDependencyConflictId()) && "jar".equals(artifact.getType()) && 
						//artifact.getFile().getAbsolutePath().contains(this.includeTagLibrariesOfPackage.replaceAll("\\.", separator)))
						this.includePackageTagLibrary(artifact.getFile().getAbsolutePath(), tagLibrariesPackages))
				{
					File file = artifact.getFile();
					JarFile jarFile = new JarFile(file);
			
					Enumeration<JarEntry> jarEntries = jarFile.entries();
					while (jarEntries.hasMoreElements()) {
						JarEntry jarEntry = jarEntries.nextElement();
						String name = jarEntry.getName();
				
						if (name.startsWith("META-INF/") && name.endsWith(".tld")) {
							List<File> taglibs = sourceMap.get(name);
					
							if (taglibs == null) {
								taglibs = new ArrayList<File>();
								sourceMap.put(name, taglibs);
							}
							taglibs.add(file);
						}
					}
				}
			}
		}
		
		List<String[]> libraries = new ArrayList<String[]>();
		
		for (Iterator<Entry<String, List<File>>> i = sourceMap.entrySet().iterator(); i.hasNext();) 
		{
			
			Map.Entry<String, List<File>> entry = i.next();
			List<File> taglibs =  entry.getValue();
			String name = entry.getKey();

			for (Iterator<File> ti = taglibs.iterator(); ti.hasNext();) 
			{
				try{File f = ti.next();
				URL url = new URL("jar:file:" + f.getAbsolutePath() + "!/" + name);
				TldContentHandler tldHandler  = new TldContentHandler();
				tldHandler.parseTld(url.openStream());
				
				String[] tag = 
				{
						tldHandler.getName(), 
						tldHandler.getVersion(),  
						(tldHandler.getJspVersion() == null || "".equals(tldHandler.getJspVersion().trim())) ? "2.1" : tldHandler.getJspVersion(),
						"@project@" + Files.getRelativeFile(projectDir, f) + "!/" + name,
						tldHandler.getURI()
				};
				libraries.add(tag);
				}catch(Exception ignore){
					//getLog().warn(ignore);
				}
			}
		}
		return libraries.toArray(new String[1][5]);
	}

	private boolean includePackageTagLibrary(String artifactPath,
			List<String> packages) {
		for (String package_ : packages) {
			if (artifactPath.contains(package_)) {
				return true;
			}
		}
		return false;
	}

	private void addTagLibraries(String[][] libraries, Xpp3Dom projectDOM, Xpp3Dom targetLibsDOM) throws XmlPullParserException {
		
		for (int i = 0, j = 0; i < libraries.length; i++, j = 0)
		{
			if (libraries[i] == null)
				continue;
				
			Xpp3Dom hashDOM = new Xpp3Dom("hash");
			Xpp3Dom hashBaseDOM = new Xpp3Dom("hash");
			Xpp3Dom valueDOM = null;
			
			hashBaseDOM.setAttribute("n", "baseLibrary");

			// Add baseLibrary hash to parent hash
			hashDOM.addChild(hashBaseDOM);

			valueDOM = new Xpp3Dom("value");
			valueDOM.setAttribute("n", "name");
			valueDOM.setAttribute("v", libraries[i][j]);
			hashBaseDOM.addChild(valueDOM);

			valueDOM = new Xpp3Dom("value");
			valueDOM.setAttribute("n", "name");
			valueDOM.setAttribute("v", libraries[i][j++]);
			hashDOM.addChild(valueDOM);

			// n="version" v=<taglib version> in baseLibrary
			valueDOM = new Xpp3Dom("value");
			valueDOM.setAttribute("n", "version");
			valueDOM.setAttribute("v", libraries[i][j]);
			hashBaseDOM.addChild(valueDOM);

			valueDOM = new Xpp3Dom("value");
			valueDOM.setAttribute("n", "version");
			valueDOM.setAttribute("v", libraries[i][j++]);
			hashDOM.addChild(valueDOM);
		
			valueDOM = new Xpp3Dom("value");
			valueDOM.setAttribute("n", "jspVersion");
			valueDOM.setAttribute("v", libraries[i][j++]);
			hashDOM.addChild(valueDOM);

			valueDOM = new Xpp3Dom("value");
			valueDOM.setAttribute("n", "tldURL");
			valueDOM.setAttribute("v", libraries[i][j++]);
			hashDOM.addChild(valueDOM);

			valueDOM = new Xpp3Dom("value");
			valueDOM.setAttribute("n", "URI");
			valueDOM.setAttribute("v", libraries[i][j++]);
			hashDOM.addChild(valueDOM);

			if (hashDOM != null)
				targetLibsDOM.addChild(hashDOM);
		}
		
	}

	private void replaceParameters(String projectName, Xpp3Dom projectDOM)
			throws XmlPullParserException {
		if ((_releaseMajor >= 11) && (projectName != null)) {
			replaceCompiler(projectDOM);
			replaceMakeProjectAndRunTarget(projectDOM);
		}
	}

	private void replaceCompiler(Xpp3Dom projectDOM)
			throws XmlPullParserException {
		// /jpr:project
		// <hash n="oracle.jdeveloper.compiler.OjcConfiguration">
		// <value n="assertionsEnabled" v="true"/>
		// <value n="compiler.name" v="Ojc"/>
		// <list n="copyRes">
		// <string v=".java"/>
		Xpp3Dom configDOM = findNamedChild(projectDOM, "hash",
				"oracle.jdeveloper.compiler.OjcConfiguration");
		Xpp3Dom compilerDOM = findNamedChild(configDOM, "value",
				"compiler.name");
		String compilerValue = "Ojc";

		if ((compiler != null) && "Javac".equalsIgnoreCase(compiler)) {
			compilerValue = "Javac";
		}
		compilerDOM.setAttribute("v", compilerValue);
	}

	private void replaceTechnologiesScopes(String projectName,
			Xpp3Dom projectDOM) {
		String[] defaultTechnologies = { "ADF_FACES", "ADFbc", "ADFm", "Ant",
				"HTML", "JAVASCRIPT", "JSF", "JSP", "Java", "JSP", "Maven",
				"XML" };

		Xpp3Dom technologyDOM = findNamedChild(projectDOM, "hash",
				"oracle.ide.model.TechnologyScopeConfiguration");
		Xpp3Dom technologyScopeDOM = findNamedChild(technologyDOM, "list",
				"technologyScope");

		String[] technologies = (this.technologiesScope != null && this.technologiesScope.length > 0) ? this.technologiesScope
				: defaultTechnologies;

		for (String techonology : technologies) {
			Xpp3Dom scopeDOM = new Xpp3Dom("string");
			scopeDOM.setAttribute("v", techonology);
			technologyScopeDOM.addChild(scopeDOM);
		}
	}

	private void replaceMavenRunConfiguration(File projectDir,  String projectName, Xpp3Dom projectDOM) {
		Xpp3Dom mavenRunConfiguration = findNamedChild(projectDOM, "hash", "oracle.jdeveloper.maven.compiler.MavenRunConfiguration");
		Xpp3Dom availableGoalList = findNamedChild(mavenRunConfiguration, "list", "availableGoalList");
		Xpp3Dom selectedGoalList = findNamedChild(mavenRunConfiguration, "list", "selectedGoalList");

		String[] availableGoalLists = new String[] { "clean", "compile",
				"deploy", "generate-resources", "generate-sources",
				"generate-test-resources", "generate-test-sources",
				"initialize", "install", "integration-test", "post-clean",
				"post-integration-test", "post-site", "pre-clean",
				"pre-integration-test", "prepare-package", "pre-site",
				"process-classes", "process-resources", "process-sources",
				"process-test-classes", "process-test-resources",
				"process-test-sources", "site", "site-deploy", "test",
				"test-compile", "validate", "verify" };

		for (String goal : availableGoalLists) {
			Xpp3Dom goalDom = new Xpp3Dom("string");
			goalDom.setAttribute("v", goal);
			availableGoalList.addChild(goalDom);
		}

		final String[] defaultGoals = { "clean package", "clean package install", "jdev:jdev" };
		List<String> options = getProfilesExecution(this.project);
		List<String> goals = new ArrayList<String>(Arrays.asList(defaultGoals));
		
		for(String option: options)
		{
			for(String goal: defaultGoals)
			{
				goals.add(goal + option);
			}
		}
		
		for (String goal : goals) {
			Xpp3Dom goalDom = new Xpp3Dom("string");
			goalDom.setAttribute("v", goal);
			selectedGoalList.addChild(goalDom);
		}
		
		String pathMavenHome = System.getenv().get("MAVEN_HOME");
		
		if (pathMavenHome == null || pathMavenHome.trim().isEmpty())
			pathMavenHome = System.getenv().get("M2_HOME");
	
		if (pathMavenHome != null && !pathMavenHome.trim().isEmpty()){ 
			//<url n="mavenHome" path="../../../../../java/apache-maven-3.0.3/"/>
			Xpp3Dom mavenHome = new Xpp3Dom("url");
			mavenHome.setAttribute("n", "mavenHome");
			mavenHome.setAttribute("path", Files.getRelativeDir(projectDir, new File(pathMavenHome)));
			mavenRunConfiguration.addChild(mavenHome);

			//<value n="mavenVersion" v="Maven version: 3.0.3"/>
			Xpp3Dom mavenVersion = new Xpp3Dom("value");
			mavenVersion.setAttribute("n", "mavenVersion");
			mavenVersion.setAttribute("v", "Maven version: " + this.runtimeInformation.getApplicationVersion().getMajorVersion() + "." + 
					this.runtimeInformation.getApplicationVersion().getMinorVersion() + "." + 
					this.runtimeInformation.getApplicationVersion().getIncrementalVersion());
			mavenRunConfiguration.addChild(mavenVersion);
		}
	}
	
	
	private List<String> getProfilesExecution(MavenProject project)
	{
		List<String> values = new ArrayList<String>();
		values.add(" -Ppackage -Plib-package ");
		
		if (project != null && project.getActiveProfiles() != null){
		    Iterator iterador = project.getActiveProfiles().iterator();
		    if (iterador != null) {
        		for(Iterator<Profile> i = iterador; i.hasNext();){
        			Profile profile = i.next();
        			values.add(String.format(" -D%s=%s ", profile.getActivation().getProperty().getName(), profile.getActivation().getProperty().getValue()));			
        		}
		    }
		}		
		/*this.getLog().info("Profiles " + this.project.getExecutionProject().getProjectBuildingRequest().getProfiles());
		for(Iterator<Profile> i = this.project.getProjectBuildingRequest().getProfiles().iterator(); i.hasNext();){
			Profile profile = i.next();
			values.add(String.format(" -D%s=%s ", profile.getActivation().getProperty().getName(), profile.getActivation().getProperty().getValue()));
		}*/		
		return values;
	}

	private void replaceDeploymentProfiles(String projectName, Xpp3Dom projectDOM) {
		String profileName = this.project.getBuild().getFinalName() .replaceAll("\\.", "_").replaceAll("-", "_");
		Xpp3Dom deployDOM = findNamedChild(projectDOM, "hash", "oracle.jdeveloper.deploy.dt.DeploymentProfiles");
		Xpp3Dom profileDefinitionsDOM = findNamedChild(deployDOM, "hash", "profileDefinitions");

		// Profile name
		final Xpp3Dom profileNameDOM = findNamedChild(profileDefinitionsDOM,
				"hash", "n");
		profileNameDOM.setAttribute("n", profileName);

		Xpp3Dom adflib = new Xpp3Dom("value");
		adflib.setAttribute("n", "adflib-connectionDetails");
		adflib.setAttribute("v", Boolean.TRUE.toString());
		profileNameDOM.addChild(adflib);

		adflib = new Xpp3Dom("value");
		adflib.setAttribute("n", "adflib-nonReusablesError");
		adflib.setAttribute("v", Boolean.FALSE.toString());
		profileNameDOM.addChild(adflib);

		Xpp3Dom archiveOptions = new Xpp3Dom("hash");
		archiveOptions.setAttribute("n", "archiveOptions");
		Xpp3Dom value = new Xpp3Dom("value");
		value.setAttribute("n", "compressed");
		value.setAttribute("v", Boolean.TRUE.toString());
		archiveOptions.addChild(value);

		value = new Xpp3Dom("value");
		value.setAttribute("n", "compressionLevel");
		value.setAttribute("v", String.valueOf(9));
		archiveOptions.addChild(value);

		value = new Xpp3Dom("value");
		value.setAttribute("n", "hasManifest");
		value.setAttribute("v", Boolean.FALSE.toString());
		archiveOptions.addChild(value);
		profileNameDOM.addChild(archiveOptions);

		// File groups
		Xpp3Dom fileGroups = new Xpp3Dom("hash");
		fileGroups.setAttribute("n", "fileGroups");

		Xpp3Dom groups = new Xpp3Dom("list");
		groups.setAttribute("n", "groups");

		// FileGroups/list/hash
		Xpp3Dom fileListGroups = new Xpp3Dom("hash");
		Xpp3Dom contributors = new Xpp3Dom("list");
		contributors.setAttribute("n", "contributors");

		Xpp3Dom hashType = new Xpp3Dom("hash");
		Xpp3Dom hashValue = new Xpp3Dom("value");
		hashValue.setAttribute("n", "type");
		hashValue.setAttribute("v", "2");
		hashType.addChild(hashValue);
		contributors.addChild(hashType);

		hashType = new Xpp3Dom("hash");
		hashValue = new Xpp3Dom("value");
		hashValue.setAttribute("n", "type");
		hashValue.setAttribute("v", "5");
		hashType.addChild(hashValue);
		contributors.addChild(hashType);
		// end contributors
		fileListGroups.addChild(contributors);

		Xpp3Dom displayName = new Xpp3Dom("value");
		displayName.setAttribute("n", "displayName");
		displayName.setAttribute("v", "Project Output");
		fileListGroups.addChild(displayName);

		/*
		 * 
		 * <hash n="filters"> <list n="rules"/> </hash>
		 */

		Xpp3Dom filters = new Xpp3Dom("hash");
		filters.setAttribute("n", "filters");
		Xpp3Dom filterList = new Xpp3Dom("list");
		filterList.setAttribute("n", "rules");
		filters.addChild(filterList);
		fileListGroups.addChild(filters);

		// end groups
		fileGroups.addChild(groups);
		// end file groups

		Xpp3Dom internalName = new Xpp3Dom("value");
		internalName.setAttribute("n", "internalName");
		internalName.setAttribute("v", "project-output");
		fileListGroups.addChild(internalName);

		Xpp3Dom groupsValueType = new Xpp3Dom("value");
		groupsValueType.setAttribute("n", "type");
		groupsValueType.setAttribute("v", "1");
		fileListGroups.addChild(groupsValueType);

		value = new Xpp3Dom("value");
		value.setAttribute("n", "type");
		value.setAttribute("v", "1");
		groups.addChild(fileListGroups);
		// end list groups

		profileNameDOM.addChild(fileGroups);

		Xpp3Dom jarUrl = new Xpp3Dom("url");
		jarUrl.setAttribute("n", "jarURL");
		// jarUrl.setAttribute("path", this.project.getBuild().getDirectory() +
		// File.separator + this.project.getBuild().getFinalName() + "." +
		// this.project.getPackaging());
		jarUrl.setAttribute("path",
				"target/" + this.project.getBuild().getFinalName() + "."
						+ this.project.getPackaging());
		profileNameDOM.addChild(jarUrl);

		// <hash n="libraryDependencies">
		// <value n="IncludeLibrariesFromOtherContainers" v="true" />
		// </hash>

		Xpp3Dom libraryDependencies = new Xpp3Dom("hash");
		libraryDependencies.setAttribute("n", "libraryDependencies");
		value = new Xpp3Dom("value");
		value.setAttribute("n", "IncludeLibrariesFromOtherContainers");
		value.setAttribute("v", Boolean.TRUE.toString());
		libraryDependencies.addChild(value);
		profileNameDOM.addChild(libraryDependencies);

		/*
		 * <hash n="LibraryDependencies"> <list n="dependencyList"/> </hash>
		 */
		libraryDependencies = new Xpp3Dom("hash");
		libraryDependencies.setAttribute("n", "LibraryDependencies");
		Xpp3Dom dependencyList = new Xpp3Dom("list");
		dependencyList.setAttribute("n", "dependencyList");
		libraryDependencies.addChild(dependencyList);
		profileNameDOM.addChild(libraryDependencies);

		// <value n="profileClass"
		// v="oracle.adfdt.common.deploy.impl.AdfLibraryProfile" />
		Xpp3Dom profileClass = new Xpp3Dom("value");
		profileClass.setAttribute("n", "profileClass");
		profileClass.setAttribute("v",
				"oracle.adfdt.common.deploy.impl.AdfLibraryProfile");
		profileNameDOM.addChild(profileClass);

		Xpp3Dom valueProfileName = new Xpp3Dom("value");
		valueProfileName.setAttribute("n", "profileName");
		valueProfileName.setAttribute("v", profileName);
		profileNameDOM.addChild(valueProfileName);
		// end profile name

		Xpp3Dom profileList = new Xpp3Dom("list");
		profileList.setAttribute("n", "profileList");
		Xpp3Dom valueProfileList = new Xpp3Dom("string");
		valueProfileList.setAttribute("v", profileName);
		profileList.addChild(valueProfileList);

		deployDOM.addChild(profileList);
	}

	private void replaceMakeProjectAndRunTarget(Xpp3Dom projectDOM)
			throws XmlPullParserException {
		// /jpr:project
		// /hash[@n="oracle.jdeveloper.runner.RunConfigurations"]
		// /hash[@n="runConfigurationDefinitions"]
		// /hash[@n="Default"]
		// /value[@n="compileBeforeRun" v="false"]
		// /url[@n="targetURL" path="src/main/webapp/index.jspx"]
		Xpp3Dom configDOM = findNamedChild(projectDOM, "hash", "oracle.jdeveloper.runner.RunConfigurations");
		Xpp3Dom defsDOM = findNamedChild(configDOM, "hash",
		"runConfigurationDefinitions");
		Xpp3Dom defaultDOM = findNamedChild(defsDOM, "hash", "Default");
		Xpp3Dom makeProjectDom = findNamedChild(defaultDOM, "value",
				"compileBeforeRun");

		if (makeProject){
			makeProjectDom.setAttribute("v", "true");
		}else {
			makeProjectDom.setAttribute("v", "false");
		}

		Xpp3Dom javaOptionsDom = findNamedChild(defaultDOM, "value",
				"javaOptions");

		if (javaOptions != null) 
		{
			javaOptionsDom.setAttribute("v", javaOptions);			
		}
		
		if (this.remoteDebuggingProfiling) 
		{
			//<value n="remoteDebuggingProfiling" v="true"/>
			Xpp3Dom remoteDebugging = new Xpp3Dom("value");
			remoteDebugging.setAttribute("n", "remoteDebuggingProfiling");
			remoteDebugging.setAttribute("v", "true");		
			defaultDOM.addChild(remoteDebugging);
		}

		if ((runTarget != null) && !"".equals(runTarget)) {
			// Convert file separator chars to generic
			String targetURL = "";
			if (File.separatorChar == '/')
				targetURL = runTarget.replace('\\', '/'); // Unix
			else
				targetURL = runTarget.replace('/', '\\'); // Windows

			Xpp3Dom targetDOM = new Xpp3Dom("url");
			targetDOM.setAttribute("path", runTarget);
			targetDOM.setAttribute("n", targetURL);
			defaultDOM.addChild(targetDOM);
		}			
		return;
	}

	private void copyTagLibraries(File projectDir, List dependencies, List artifacts) throws IOException {
		
		File targetDir = new File(projectDir, "src/main/webapp/WEB-INF");

		for (Iterator i = dependencies.iterator(); i.hasNext();) {
			Dependency dependency = (Dependency) i.next();
			MavenProject dependentProject = findDependentProject(dependency.getManagementKey());
			
			if (dependentProject != null) {
				List resourceRoots = dependentProject.getResources();
				
				for (Iterator j = resourceRoots.iterator(); j.hasNext();) {
					Resource resource = (Resource) j.next();
					String resourceRoot = resource.getDirectory();
					File resourceDirectory = new File(resourceRoot);
					
					if (resourceDirectory.exists()) {
						DirectoryScanner scanner = new DirectoryScanner();
						scanner.setBasedir(resourceRoot);
						scanner.addDefaultExcludes();
						scanner.setIncludes(new String[] { "META-INF/*.tld" });
						scanner.scan();

						String[] tldFiles = scanner.getIncludedFiles();
						for (int k = 0; k < tldFiles.length; k++) {
							File sourceFile = new File(resourceDirectory, tldFiles[k]);
							File targetFile = new File(targetDir, sourceFile.getName());

							if (targetFile.exists())
								targetFile.delete();
							
							FileUtils.copyFile(sourceFile, targetFile);
						}
					}
				}
			}
		}

		Map sourceMap = new TreeMap();

		for (Iterator i = artifacts.iterator(); i.hasNext();) {
			Artifact artifact = (Artifact) i.next();
			if (!isDependentProject(artifact.getDependencyConflictId()) && "jar".equals(artifact.getType())) {
				File file = artifact.getFile();
				JarFile jarFile = new JarFile(file);
				Enumeration jarEntries = jarFile.entries();
				while (jarEntries.hasMoreElements()) {
					JarEntry jarEntry = (JarEntry) jarEntries.nextElement();
					String name = jarEntry.getName();
					if (name.startsWith("META-INF/") && name.endsWith(".tld")) {
						List taglibs = (List) sourceMap.get(name);
						if (taglibs == null) {
							taglibs = new ArrayList();
							sourceMap.put(name, taglibs);
						}
						taglibs.add(file);
					}
				}
			}
		}

		for (Iterator i = sourceMap.entrySet().iterator(); i.hasNext();) {
			Map.Entry e = (Map.Entry) i.next();
			List taglibs = (List) e.getValue();
			String name = (String) e.getKey();

			for (Iterator ti = taglibs.iterator(); ti.hasNext();) {
				File file = (File) ti.next();
				File sourceFile = new File(name);
				StringBuffer buff = new StringBuffer(sourceFile.getName());
				if (taglibs.size() > 1) {
					String jarName = file.getName().substring(0, file.getName().length() - ".jar".length());
					buff.insert(buff.length() - ".tld".length(), "-" + jarName);
				}

				URL jarURL = file.toURL();
				URL sourceURL = new URL("jar:" + jarURL.toExternalForm() + "!/" + name);
				File targetFile = new File(targetDir, buff.toString());
				
				if (targetFile.exists())
					targetFile.delete();
				FileUtils.copyURLToFile(sourceURL, targetFile);
				targetFile.setReadOnly();
			}
		}
	}

	private void replaceOutputDirectory(File projectDir, File outputDir,
			Xpp3Dom projectDOM) throws XmlPullParserException {
		// /jpr:project
		// /hash[@n="oracle.jdevimpl.config.JProjectPaths"]

		Xpp3Dom projectPathsDOM = findNamedChild(projectDOM, "hash",
				"oracle.jdevimpl.config.JProjectPaths");
		Xpp3Dom sourceDOM = new Xpp3Dom("url");

		//
		// <url @n="outputDirectory" path="[relative-path-to-output-dir]" />
		//
		sourceDOM.setAttribute("path", getRelativeDir(projectDir, outputDir));
		sourceDOM.setAttribute("n", "outputDirectory");
		projectPathsDOM.addChild(sourceDOM);
	}

	/**
	 * Returns the JDeveloper project file for a Maven POM.
	 * 
	 * @param project
	 *            the Maven POM
	 * 
	 * @return the JDeveloper project file
	 */
	private File getJProjectFile(MavenProject project) {
		String jprName = project.getArtifactId() + ".jpr";
		return new File(project.getBasedir(), jprName);
	}

	/**
	 * Returns the JDeveloper test project file for a Maven POM.
	 * 
	 * @param project
	 *            the Maven POM
	 * 
	 * @return the JDeveloper test project file
	 */
	private File getJProjectTestFile(MavenProject project) {
		String jprName = project.getArtifactId() + "-test.jpr";
		return new File(project.getBasedir(), jprName);
	}

	/**
	 * Returns the JDeveloper workspace file for a Maven POM.
	 * 
	 * @param project
	 *            the Maven POM
	 * 
	 * @return the JDeveloper workspace file
	 */
	private File getJWorkspaceFile(MavenProject project) {
		String jwsName = project.getArtifactId() + ".jws";
		return new File(project.getBasedir(), jwsName);
	}

	/**
	 * Reads a JDeveloper workspace file into DOM.
	 * 
	 * @param workspaceFile
	 *            the JDeveloper workspace file
	 * 
	 * @return the parsed DOM
	 */
	private Xpp3Dom readWorkspaceDOM(File workspaceFile) throws IOException,
			XmlPullParserException {
		return readDOM(workspaceFile, release + "/workspace.xml");
	}

	/**
	 * Reads a JDeveloper project file into DOM.
	 * 
	 * @param projectFile
	 *            the JDeveloper project file
	 * 
	 * @return the parsed DOM
	 */
	private Xpp3Dom readProjectDOM(File projectFile) throws IOException,
			XmlPullParserException {
		return readDOM(projectFile, release + "/project.xml");
	}

	/**
	 * Reads a source file into DOM, defaulting to a packaged resource if the
	 * source file does not already exist.
	 * 
	 * @param sourceFile
	 *            the source file to be parsed
	 * @param resourcePath
	 *            the default packaged resource
	 * 
	 * @return the parsed DOM
	 */
	private Xpp3Dom readDOM(File sourceFile, String resourcePath)
			throws IOException, XmlPullParserException {
		Reader reader = null;
		try {
			if (!force && sourceFile.exists()) {
				// parse the existing source file
				reader = new FileReader(sourceFile);
			} else {
				// parse the default resource file
				URL resource = getClass().getResource(resourcePath);

				// ensure that the resourcePath can be found
				if (resource == null)
					throw new IOException("Unable to read resource: "
							+ resourcePath);

				reader = new InputStreamReader(resource.openStream());
			}
			return Xpp3DomBuilder.build(reader);
		} finally {
			IOUtil.close(reader);
		}
	}

	/**
	 * Writes a XML DOM to the target file.
	 * 
	 * @param targetFile
	 *            the target file
	 * @param sourceDOM
	 *            the source DOM
	 */
	private void writeDOM(File targetFile, Xpp3Dom sourceDOM)
			throws IOException, XmlPullParserException {
		FileWriter writer = null;
		try {
			writer = new FileWriter(targetFile);
			Xpp3DomWriter.write(writer, sourceDOM);
		} finally {
			IOUtil.close(writer);
		}
	}

	private String getRelativeDir(File source, File target) {
		return getRelativePath(source, target, true);
	}

	private String getRelativeFile(File source, File target) {
		return getRelativePath(source, target, false);
	}

	/**
	 * Returns the relative path between two files.
	 * 
	 * @param source
	 *            the source file
	 * @param target
	 *            the target file
	 * 
	 * @return the relative path between two files
	 */
	private String getRelativePath(File source, File target, boolean isDirectory) {
		String sourcePath = source.getAbsolutePath();
		String targetPath = target.getAbsolutePath();

		if (targetPath.startsWith(sourcePath + File.separatorChar)) {
			String relativePath = targetPath.substring(sourcePath.length() + 1);
			relativePath = relativePath.replace(File.separatorChar, '/');
			if (isDirectory)
				relativePath += "/";
			return relativePath;
		} else {
			String[] sourcePaths = sourcePath.split("\\" + File.separator);
			String[] targetPaths = targetPath.split("\\" + File.separator);

			// On Windows, the first element in the absolute path is a drive
			// letter
			if (System.getProperty("os.name").startsWith("Windows")) {
				// uppercase the drive letter because Cygwin sometimes delivers
				// a lowercase drive letter
				sourcePaths[0] = sourcePaths[0].toUpperCase();
				targetPaths[0] = targetPaths[0].toUpperCase();
			}

			int sourcePathCount = sourcePaths.length;
			int targetPathCount = targetPaths.length;
			int commonPathCount = 0;

			int minPathCount = Math.min(sourcePathCount, targetPathCount);
			for (int i = 0; i < minPathCount; i++) {
				if (sourcePaths[i].equals(targetPaths[i]))
					commonPathCount++;
			}

			if (commonPathCount > 0) {
				int sourceRelativePathCount = sourcePathCount - commonPathCount;
				int targetRelativePathCount = targetPathCount - commonPathCount;

				int relativePathCount = sourceRelativePathCount
						+ targetRelativePathCount;
				String[] relativePaths = new String[relativePathCount];

				for (int i = 0; i < sourceRelativePathCount; i++) {
					relativePaths[i] = "..";
				}

				for (int i = 0; i < targetRelativePathCount; i++) {
					relativePaths[sourceRelativePathCount + i] = targetPaths[commonPathCount
							+ i];
				}

				// join
				StringBuffer relativePath = new StringBuffer();
				for (int i = 0; i < relativePathCount; i++) {
					if (i > 0)
						relativePath.append("/");
					relativePath.append(relativePaths[i]);
				}
				return relativePath.toString();
			} else {
				return targetPath;
			}
		}
	}

	private Xpp3Dom findNamedChild(Xpp3Dom parent, String childName,
			String attrValue) {
		Xpp3Dom[] hash = parent.getChildren(childName);
		for (int i = 0; i < hash.length; i++) {
			if (attrValue.equals(hash[i].getAttribute("n")))
				return hash[i];
		}

		Xpp3Dom child = new Xpp3Dom(childName);
		child.setAttribute("n", attrValue);
		parent.addChild(child);

		return child;
	}

	private MavenProject findDependentProject(String dependencyManagementKey) {
		for (Iterator i = reactorProjects.iterator(); i.hasNext();) {
			MavenProject reactorProject = (MavenProject) i.next();
			String ident = reactorProject.getArtifact()
					.getDependencyConflictId();
			if (ident.equals(dependencyManagementKey))
				return reactorProject.getExecutionProject();
		}

		return null;
	}

	private boolean isDependentProject(String dependencyManagementKey) {
		return (findDependentProject(dependencyManagementKey) != null);
	}

	private void removeChildren(Xpp3Dom parent) {
		while (parent.getChildCount() != 0)
			parent.removeChild(0);
	}

	private Xpp3Dom createProjectReferenceDOM(File workspaceDir,
			File projectFile) {
		Xpp3Dom hashDOM = new Xpp3Dom("hash");
		Xpp3Dom urlDOM = new Xpp3Dom("url");
		urlDOM.setAttribute("n", "URL");
		urlDOM.setAttribute("path", getRelativeFile(workspaceDir, projectFile));

		if (_releaseMajor < 11) {
			Xpp3Dom valueDOM = new Xpp3Dom("value");
			valueDOM.setAttribute("n", "nodeClass");
			valueDOM.setAttribute("v", "oracle.jdeveloper.model.JProject");
			hashDOM.addChild(valueDOM);
		}
		hashDOM.addChild(urlDOM);
		return hashDOM;
	}

	private void _parseRelease() {
		String nums[] = release.split("\\.");
		try {
			_releaseMajor = Integer.parseInt(nums[0]);
		} catch (NumberFormatException e) {
			// Default release is currently 10.x
			_releaseMajor = 10;
		}
		getLog().info(
				"releaseMajor is " + Integer.valueOf(_releaseMajor).toString());
	}
	
	private boolean isWebProject(){
		return (this.webProject || "war".equalsIgnoreCase(this.project.getPackaging()));
	}

	private int _releaseMajor = 0;
	private boolean _addLibraries = true;
	private boolean _addTagLibs = true;

	private static final String _PROPERTY_ADD_LIBRARY = "jdev.plugin.add.libraries";
	private static final String _PROPERTY_ADD_TAGLIBS = "jdev.plugin.add.taglibs";

	// Does nothing in v10.1.3
	private static final String _PROPERTY_HAS_TESTS = "jdev.project.has.tests";
}
