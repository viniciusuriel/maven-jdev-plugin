package org.apache.myfaces.trinidadbuild.plugin.jdeveloper;

import java.io.File;

public class Files {

	public static String getRelativeDir(File source, File target) {
		return getRelativePath(source, target, true);
	}

	public static String getRelativeFile(File source, File target) {
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
	public static String getRelativePath(File source, File target,
			boolean isDirectory) {
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
}