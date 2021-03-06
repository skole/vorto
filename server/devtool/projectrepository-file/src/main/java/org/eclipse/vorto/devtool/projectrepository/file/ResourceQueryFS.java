/**
 * Copyright (c) 2015-2016 Bosch Software Innovations GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * The Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Bosch Software Innovations GmbH - Please refer to git log
 */
package org.eclipse.vorto.devtool.projectrepository.file;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.eclipse.vorto.devtool.projectrepository.IProjectRepositoryService;
import org.eclipse.vorto.devtool.projectrepository.model.FileResource;
import org.eclipse.vorto.devtool.projectrepository.model.FolderResource;
import org.eclipse.vorto.devtool.projectrepository.model.ModelResource;
import org.eclipse.vorto.devtool.projectrepository.model.ProjectResource;
import org.eclipse.vorto.devtool.projectrepository.model.Resource;
import org.eclipse.vorto.devtool.projectrepository.model.ResourceType;
import org.eclipse.vorto.devtool.projectrepository.query.IResourceQuery;
import org.eclipse.vorto.devtool.projectrepository.query.TextFilter;
import org.eclipse.vorto.devtool.projectrepository.utils.ProjectRepositoryConstants;
import org.eclipse.vorto.repository.api.ModelType;

/**
 * File system based implementation of {@link IProjectRepositoryService}
 */
public class ResourceQueryFS extends AbstractQueryJxPath {

	private String rootDirectory;

	private List<File> fileTree = new ArrayList<File>();

	public ResourceQueryFS(String rootDirectory) {
		this.rootDirectory = rootDirectory;
	}

	public ResourceQueryFS path(String path) {
		TextFilter tf = new TextFilter();
		tf.setKey("path");
		tf.setText(path);
		tf.setWhereCondition("/.[path='?']");
		addFilter(tf);

		return this;
	}

	@Override
	public IResourceQuery pathLike(String path) {
		TextFilter tf = new TextFilter();
		tf.setKey("pathLike");
		tf.setText(path.replace(File.separator, "/"));
		tf.setWhereCondition("/.[contains(path,'?')]");
		addFilter(tf);

		return this;
	}

	public ResourceQueryFS author(String author) {
		TextFilter tf = new TextFilter();
		tf.setKey("author");
		tf.setText(author);
		tf.setWhereCondition("/.[author='?']");
		addFilter(tf);

		return this;
	}

	@Override
	public IResourceQuery name(String name) {
		TextFilter tf = new TextFilter();
		tf.setKey("name");
		tf.setText(name);
		tf.setWhereCondition("/.[name='?']");
		addFilter(tf);

		return this;
	}

	@Override
	public IResourceQuery namespace(String namespace) {
		TextFilter tf = new TextFilter();
		tf.setKey("namespace");
		tf.setText(namespace);
		tf.setWhereCondition("/.[namespace='?']");
		addFilter(tf);

		return this;
	}

	@Override
	public IResourceQuery nameLike(String name) {
		TextFilter tf = new TextFilter();
		tf.setKey("nameLike");
		tf.setText(name);
		tf.setWhereCondition("/.[contains(name,'?')]");
		addFilter(tf);

		return this;
	}

	@Override
	public IResourceQuery property(String propertyName, String propertyValue) {
		TextFilter tf = new TextFilter();
		tf.setKey("property");
		tf.setText(propertyValue);
		tf.setWhereCondition("/.[properties[@name = '" + propertyName + "'] = '?']");
		addFilter(tf);
		return this;
	}

	@Override
	public IResourceQuery version(String version) {
		throw new UnsupportedOperationException();
	}

	public List<Resource> getAll() {
		List<Resource> result = new ArrayList<Resource>();

		File[] files = listFileTree();

		for (File file : files) {
			result.add(createResource(file));
		}

		return result;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Resource createResource(File file) {
		Resource resource;
		Properties props = new Properties();
		File metaFile;
		if (file.isDirectory()) {
			metaFile = new File(file, ProjectRepositoryServiceFS.META_PROPERTY_FILENAME);
		} else {
			resource = new FileResource();
			metaFile = new File(file.getParentFile(),
					"." + file.getName() + ProjectRepositoryServiceFS.META_PROPERTY_FILENAME);
		}

		try {
			props.loadFromXML(new FileInputStream(metaFile));
		} catch (Exception ex) {
			// Nothing to do here.
		}

		if (file.isDirectory()) {
			if (props.containsKey(ProjectRepositoryConstants.META_PROPERTY_IS_PROJECT)) {
				boolean isProject = Boolean
						.parseBoolean((String) props.get(ProjectRepositoryConstants.META_PROPERTY_IS_PROJECT));
				if (isProject) {
					resource = new ProjectResource();
				} else {
					resource = new FolderResource();
				}
			} else {
				resource = new FolderResource();
			}
			resource.setName(file.getName());
		} else {
			if(props.containsKey(ProjectRepositoryConstants.META_PROPERTY_MODEL_TYPE)){
				ModelResource modelResource = (ModelResource) createModelResource(
						new HashMap(props));
				try {
					modelResource.setContent(FileUtils.readFileToByteArray(file));
				} catch (Exception ex) {

				}
				resource = modelResource;				
			}else{
				resource = new FileResource();
				resource.setName(file.getName());
			}
		}

		String resourcePath = file.getPath().replace(rootDirectory + File.separator, "").replace(File.separator, "/");
		resource.setPath(resourcePath);
		if (props.containsKey((ProjectRepositoryServiceFS.META_PROPERTY_CREATIONDATE))) {
			resource.setCreationDate(new Date(
					Long.parseLong((String) props.get(ProjectRepositoryServiceFS.META_PROPERTY_CREATIONDATE))));
		}
		resource.setLastModified(new Date(file.lastModified()));
		resource.setProperties(new HashMap(props));
		return resource;
	}

	@Override
	public IResourceQuery lastModified(Date date) {
		TextFilter tf = new TextFilter();
		tf.setKey("lastModified");
		tf.setText(date);
		tf.setWhereCondition("/.[contains(lastModified,'?')]");
		addFilter(tf);

		return this;
	}

	@Override
	public IResourceQuery type(ResourceType type) {
		TextFilter tf = new TextFilter();
		tf.setKey("type");
		tf.setText(type);
		tf.setWhereCondition("/.[type='?']");
		addFilter(tf);

		return this;
	}

	private File[] listFileTree() {
		File[] files = new File(rootDirectory).listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return !file.getName().contains(ProjectRepositoryServiceFS.META_PROPERTY_FILENAME);
			}
		});

		for (File file : files) {
			getChildren(file);
		}

		return fileTree.toArray(files);

	}

	private void getChildren(File file) {
		fileTree.add(file);

		if (file.isDirectory()) {

			for (File child : file.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					return !file.getName().contains(ProjectRepositoryServiceFS.META_PROPERTY_FILENAME);
				}
			})) {
				getChildren(child);
			}
		}
	}

	private ModelResource createModelResource(Map<String, String> properties) {
		ModelResource modelResource = new ModelResource();
		modelResource.setName(properties.get(ProjectRepositoryConstants.META_PROPERTY_NAME));
		modelResource.setNamespace(properties.get(ProjectRepositoryConstants.META_PROPERTY_NAMESPACE));
		modelResource.setVersion(properties.get(ProjectRepositoryConstants.META_PROPERTY_VERSION));
		modelResource
				.setModelType(ModelType.valueOf(properties.get(ProjectRepositoryConstants.META_PROPERTY_MODEL_TYPE)));
		modelResource
				.setSubType(properties.get(ProjectRepositoryConstants.META_PROPERTY_MODEL_SUB_TYPE));
		return modelResource;
	}
}

/* EOF */
