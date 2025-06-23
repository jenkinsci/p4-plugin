package org.jenkinsci.plugins.p4.utils;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import com.mig82.folders.properties.FolderProperties;
import hudson.model.ItemGroup;
import hudson.util.DescribableList;
import jenkins.scm.api.SCMSourceOwner;
import org.jenkinsci.plugins.p4.workspace.Expand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class FolderPropertiesUtil {
	public static final String PROP_EXPANSION_PATTERN = "${";

	private FolderPropertiesUtil() {
	}

	public static List<String> processFolderPropertiesIn(List<String> paths, SCMSourceOwner owner) {
		if (paths == null || paths.isEmpty()) {
			return new ArrayList<>();
		}
		Map<String, String> folderProperties = getFolderPropertiesFrom(owner);
		if (folderProperties.isEmpty()) {
			return paths;
		}
		Expand expand = new Expand(folderProperties);
		return paths.stream()
				.map(path -> expand.format(path, false))
				.collect(Collectors.toList());

	}

	private static Map<String, String> getFolderPropertiesFrom(SCMSourceOwner owner) {
		if (owner == null) {
			return new HashMap<>();
		}
		ItemGroup<?> parent = owner.getParent();
		Map<String, String> propertyMap = new HashMap<>();
		while (parent instanceof AbstractFolder) {
			AbstractFolder<?> folder = (AbstractFolder<?>) parent;
			DescribableList<AbstractFolderProperty<?>, AbstractFolderPropertyDescriptor> properties = folder.getProperties();
			addFolderPropertiesToMap(properties, propertyMap);
			parent = folder.getParent();
		}
		return propertyMap;
	}

	private static void addFolderPropertiesToMap(DescribableList<AbstractFolderProperty<?>, AbstractFolderPropertyDescriptor> properties, Map<String, String> propertyMap) {
		if (properties == null) {
			return;
		}
		FolderProperties folderProperties = properties.get(FolderProperties.class);

		if (folderProperties == null) {
			return;
		}
		Arrays.stream(folderProperties.getProperties())
				.filter(Objects::nonNull)
				.forEach(prop -> propertyMap.put(prop.getKey(), prop.getValue()));
	}
}
