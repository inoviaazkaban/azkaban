/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.webapp.servlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.mortbay.io.WriterOutputStream;

import azkaban.project.Project;
import azkaban.server.AzkabanServer;
import azkaban.server.session.Session;
import azkaban.user.User;
import azkaban.webapp.AzkabanWebServer;

public class FileEditorServlet extends LoginAbstractAzkabanServlet {

	private static final long serialVersionUID = 1L;
	private static final String basePath = AzkabanServer.getAzkabanProperties().get("azkaban.project.dir");

	@Override
	protected void handleGet(HttpServletRequest req, HttpServletResponse resp, Session session)
			throws ServletException, IOException {
		if (hasParam(req, "resource")) {
			String resourcePath = getParam(req, "resource");
			streamResource(resourcePath, resp, session.getUser());
		} else {
			Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/fileeditor.vm");
			page.render();
		}
	}

	@Override
	protected void handlePost(HttpServletRequest req, HttpServletResponse resp, Session session)
			throws ServletException, IOException {
		if (hasParam(req, "resource")) {
			String resourcePath = getParam(req, "resource");
			String content = req.getParameter("content");
			saveResource(resourcePath, content);
		}
	}

	private void streamResource(String resourcePath, HttpServletResponse response, User user)
			throws IOException, ServletException {

		File resource = new File(basePath + resourcePath);
		OutputStream out = null;
		try {
			out = response.getOutputStream();
		} catch (IllegalStateException e) {
			out = new WriterOutputStream(response.getWriter());
		}

		if (resource.exists()) {
			if (resource.isDirectory()) {
				String directoryContent = getDirectoryContent(resourcePath, user);
				response.setContentType("text/html");
				response.setContentLength(directoryContent.length());
				response.setStatus(HttpServletResponse.SC_OK);
				out.write(directoryContent.toString().getBytes());
			} else {
				response.setContentType("text/plain");
				response.setStatus(HttpServletResponse.SC_OK);
				int length = streamFileContent(resourcePath, out);
				response.setContentLength(length);
			}
		}
	}

	private String getDirectoryContent(String resourcePath, User user) {
		StringBuffer directoryContent = new StringBuffer();

		// Fetches all sub folders and files recursively
		Collection<File> files = FileUtils.listFilesAndDirs(new File(basePath + resourcePath), TrueFileFilter.INSTANCE,
				TrueFileFilter.INSTANCE);

		directoryContent.append("<table border=0>");

		// Insert a link to the parent folder only, if the resource is not the
		// webserver's context root
		if (!"/".equals(resourcePath) && resourcePath != null && resourcePath.length() > 0) {
			File parent = new File(basePath + resourcePath).getParentFile();
			directoryContent.append("<tr><td><a href=\"#\" onclick=\"browseDirectory('"
					+ parent.getAbsolutePath().substring(basePath.length()) + "/')\">[Parent Folder]</td></tr>");
		}

		// Fetch all projects accessible to the current session user
		List<Project> projects = ((AzkabanWebServer) getApplication()).getProjectManager().getUserProjects(user);
		List<String> projectNames = new ArrayList<String>();
		for (Project project : projects) {
			projectNames.add(project.getName());
		}

		for (File file : files) {
			// Filter out only those folders/files which are immediate children
			// of the queried resource
			if (file.getParent().equals(new File(basePath + resourcePath).getPath())) {
				// If the folder is at the first level under context root, then
				// it should be an azkaban project
				if (file.getParent().equals(new File(basePath).getPath())) {
					// The first level should contain only azkaban project
					// folders
					if (file.isDirectory()) {
						String displayName = file.getName();
						// The project name of this folder is in a special text
						// file named "projectname", one level under this folder
						Scanner in = null;
						try {
							in = new Scanner(new File(file.getAbsolutePath() + File.separator + "projectname"));
							displayName = in.nextLine();
						} catch (FileNotFoundException e) {
							// The default displayName is used in case of an
							// Exception
						} finally {
							if (in != null) {
								in.close();
							}
						}
						// The access to this folder is filtered out
						// based on the currently session user
						if (projectNames.contains(displayName)) {
							directoryContent
									.append("<tr><td>&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#\" onclick=\"browseDirectory('"
											+ resourcePath + file.getName() + "/')\">" + displayName + "/</td></tr>");
						}
					}
				}
				// The folder is NOT at the first level under context root, then
				// hence is a deeper sub folder under one of the azkaban project
				else {
					// All folders at this deeper level should be accessible
					if (file.isDirectory()) {
						String displayName = file.getName();
						directoryContent
								.append("<tr><td>&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#\" onclick=\"browseDirectory('"
										+ resourcePath + file.getName() + "/')\">" + displayName + "/</td></tr>");
					}
					// All files at this deeper level should be accessible,
					// except the special ones namd "projectname"
					else {
						if (!"projectname".equals(file.getName())) {
							directoryContent
									.append("<tr><td>&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#\" onclick=\"fetchFileContent('"
											+ resourcePath + file.getName() + "')\">" + file.getName() + "</td></tr>");
						}
					}
				}
			}
		}
		directoryContent.append("</table><br>");
		return directoryContent.toString();
	}

	private int streamFileContent(String resourcePath, OutputStream out) throws IOException {
		int len, totalLength = 0;
		FileInputStream in = null;
		try {
			in = new FileInputStream(basePath + resourcePath);
			int bufferSize = 2 * 8192;
			byte buffer[] = new byte[bufferSize];

			while (true) {
				len = in.read(buffer, 0, bufferSize);
				totalLength += len;
				if (len < 0)
					break;
				out.write(buffer, 0, len);
			}
		} finally {
			if (in != null) {
				in.close();
			}
		}
		return totalLength + 1;
	}

	private void saveResource(String resourcePath, String content) throws IOException, ServletException {
		OutputStream out = null;
		try {
			File resource = new File(basePath + resourcePath);
			if (resource.exists() && resource.isFile()) {
				out = new FileOutputStream(resource);
				out.write((content + "").getBytes());
			}
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}
}
