/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.uberfire.java.nio.fs.k8s;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.uberfire.java.nio.IOException;
import org.uberfire.java.nio.base.AbstractBasicFileAttributeView;
import org.uberfire.java.nio.base.BasicFileAttributesImpl;
import org.uberfire.java.nio.base.GeneralPathImpl;
import org.uberfire.java.nio.channels.SeekableByteChannel;
import org.uberfire.java.nio.file.AccessDeniedException;
import org.uberfire.java.nio.file.AccessMode;
import org.uberfire.java.nio.file.AtomicMoveNotSupportedException;
import org.uberfire.java.nio.file.CopyOption;
import org.uberfire.java.nio.file.DeleteOption;
import org.uberfire.java.nio.file.DirectoryNotEmptyException;
import org.uberfire.java.nio.file.DirectoryStream;
import org.uberfire.java.nio.file.FileAlreadyExistsException;
import org.uberfire.java.nio.file.FileStore;
import org.uberfire.java.nio.file.LinkOption;
import org.uberfire.java.nio.file.NoSuchFileException;
import org.uberfire.java.nio.file.NotDirectoryException;
import org.uberfire.java.nio.file.NotLinkException;
import org.uberfire.java.nio.file.OpenOption;
import org.uberfire.java.nio.file.Path;
import org.uberfire.java.nio.file.attribute.BasicFileAttributes;
import org.uberfire.java.nio.file.attribute.FileAttribute;
import org.uberfire.java.nio.file.attribute.FileAttributeView;
import org.uberfire.java.nio.fs.cloud.CloudClientFactory;
import org.uberfire.java.nio.fs.file.SimpleFileSystemProvider;

import static org.kie.soup.commons.validation.PortablePreconditions.checkCondition;
import static org.kie.soup.commons.validation.PortablePreconditions.checkNotNull;
import static org.uberfire.java.nio.fs.k8s.K8SFileSystemUtils.CFG_MAP_FSOBJ_CONTENT_KEY;
import static org.uberfire.java.nio.fs.k8s.K8SFileSystemUtils.CFG_MAP_LABEL_FSOBJ_NAME_KEY_PREFIX;
import static org.uberfire.java.nio.fs.k8s.K8SFileSystemUtils.CFG_MAP_LABEL_FSOBJ_SIZE_KEY;
import static org.uberfire.java.nio.fs.k8s.K8SFileSystemUtils.createOrReplaceFSCM;
import static org.uberfire.java.nio.fs.k8s.K8SFileSystemUtils.createOrReplaceParentDirFSCM;
import static org.uberfire.java.nio.fs.k8s.K8SFileSystemUtils.getFsObjCM;
import static org.uberfire.java.nio.fs.k8s.K8SFileSystemUtils.isDirectory;

public class K8SFileSystemProvider extends SimpleFileSystemProvider implements CloudClientFactory {

    public K8SFileSystemProvider() {
        super(null, OSType.UNIX_LIKE);
        this.fileSystem = new K8SFileSystem(this, "/");
    }

    @Override
    public String getScheme() {
        return "k8s";
    }

    @Override
    public InputStream newInputStream(final Path path,
                                      final OpenOption... options) throws IllegalArgumentException, 
        NoSuchFileException, IOException, SecurityException {
        checkNotNull("path", path);
        return Channels.newInputStream(new K8SFileChannel(path));
    }

    @Override
    public OutputStream newOutputStream(final Path path,
                                        final OpenOption... options) throws IllegalArgumentException, 
        UnsupportedOperationException, IOException, SecurityException {
        checkNotNull("path", path);
        return Channels.newOutputStream(new K8SFileChannel(path));
    }

    @Override
    public FileChannel newFileChannel(final Path path,
                                      final Set<? extends OpenOption> options,
                                      final FileAttribute<?>... attrs) throws IllegalArgumentException, 
        UnsupportedOperationException, IOException, SecurityException {
        checkNotNull("path", path);
        throw new UnsupportedOperationException();
    }
    
    @Override
    public SeekableByteChannel newByteChannel(final Path path,
                                              final Set<? extends OpenOption> options,
                                              final FileAttribute<?>... attrs) 
        throws IllegalArgumentException, UnsupportedOperationException, FileAlreadyExistsException, 
            IOException, SecurityException {
        return new K8SFileChannel(path);
    }

    @Override
    public void createDirectory(final Path dir,
                                final FileAttribute<?>... attrs) throws UnsupportedOperationException, 
        FileAlreadyExistsException, IOException, SecurityException {
        checkNotNull("dir",dir);

        Optional<ConfigMap> directoryCm = executeCloudFunction(client -> getFsObjCM(client, dir), KubernetesClient.class);
        if (directoryCm.isPresent()) {
            throw new FileAlreadyExistsException(dir.toString());
        }

        executeCloudFunction(client -> createOrReplaceFSCM(client, 
                                                           dir,
                                                           createOrReplaceParentDirFSCM(client, dir, 0L),
                                                           Collections.emptyMap(),
                                                           true), 
                             KubernetesClient.class);
    }

    @Override
    protected Path[] getDirectoryContent(final Path dir, final DirectoryStream.Filter<Path> filter) 
            throws NotDirectoryException {
        checkNotNull("dir", dir);
        ConfigMap dirCM = executeCloudFunction(client -> getFsObjCM(client, dir), KubernetesClient.class)
                .orElseThrow(() -> new NotDirectoryException(dir.toString()));
        if (dirCM.getData().isEmpty()) {
            throw new IOException("Directory [" + dir.toString() + "] is empty.");
        }
        
        String dirPathString = dirCM.getMetadata().getLabels().getOrDefault(CFG_MAP_LABEL_FSOBJ_NAME_KEY_PREFIX, "");
        return dirCM.getData()
                    .keySet()
                    .stream()
                    .map(fileName -> GeneralPathImpl.create(getDefaultFileSystem(), 
                                                            dirPathString.concat(fileName), true))
                    .toArray(Path[]::new);
    }

    @Override
    public void delete(final Path path, final DeleteOption... options) 
            throws NoSuchFileException, DirectoryNotEmptyException, IOException, SecurityException {
        checkNotNull("path", path);
        checkFileNotExistThenThrow(path, false);
        deleteIfExists(path, options);
    }

    @Override
    public boolean deleteIfExists(final Path path, final DeleteOption... options) 
            throws DirectoryNotEmptyException, IOException, SecurityException {
        checkNotNull("path", path);
        synchronized (this) {
            try {
                return executeCloudFunction(client -> Optional.ofNullable(getFsObjCM(client, path))
                                                              .map(cm -> client.configMaps().delete(cm))
                                                              .orElse(Boolean.FALSE), 
                                            KubernetesClient.class).get();
            } finally {
                toGeneralPathImpl(path).clearCache();
            }
        }
    }
    
    @Override
    public boolean isHidden(final Path path) throws IllegalArgumentException, IOException, SecurityException {
        checkNotNull("path", path);
        return false;
    }

    @Override
    public void checkAccess(final Path path, AccessMode... modes) throws UnsupportedOperationException,
        NoSuchFileException, AccessDeniedException, IOException, SecurityException {
        checkNotNull("path", path);
        checkNotNull("modes", modes);
        checkFileNotExistThenThrow(path, false);

        for (final AccessMode mode : modes) {
            checkNotNull("mode", mode);
            switch (mode) {
                case READ:
                    break;
                case EXECUTE:
                    throw new AccessDeniedException(path.toRealPath().toString());
                case WRITE:
                    break;
            }
        }
    }

    @Override
    public FileStore getFileStore(final Path path) throws IOException, SecurityException {
        checkNotNull("path", path);
        return new K8SFileStore(path);
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(final Path path,
                                                            final Class<A> type,
                                                            final LinkOption... options) 
        throws NoSuchFileException, UnsupportedOperationException, IOException, SecurityException {
        checkNotNull("path", path);
        checkNotNull("type", type);
        checkFileNotExistThenThrow(path, false);
        if (type == BasicFileAttributesImpl.class || type == BasicFileAttributes.class) {
            final K8SBasicFileAttributeView view = getFileAttributeView(path,
                                                                        K8SBasicFileAttributeView.class,
                                                                        options);
            return view.readAttributes();
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <V extends FileAttributeView> V createFileAttributeView(final GeneralPathImpl path, 
                                                                      final Class<V> type) {
        if (AbstractBasicFileAttributeView.class.isAssignableFrom(type)) {
            final V newView = (V) new K8SBasicFileAttributeView(path);
            path.addAttrView(newView);
            return newView;
        } else {
            return null;
        }
    }
    
    @Override
    public void copy(final Path source,
                     final Path target,
                     final CopyOption... options) throws UnsupportedOperationException, 
        FileAlreadyExistsException, DirectoryNotEmptyException, IOException, SecurityException {
        checkNotNull("source", source);
        checkNotNull("target", target);
        
        Optional<ConfigMap> srcCMOpt = executeCloudFunction(
            client -> getFsObjCM(client, source), KubernetesClient.class);
        checkCondition("source must exist", srcCMOpt.isPresent());
        checkFileExistsThenThrow(target);

        ConfigMap srcCM = srcCMOpt.get();
        if (isDirectory(srcCMOpt.get())) {
            throw new UnsupportedOperationException(srcCMOpt.get().getMetadata().getName() + "is a directory.");
        }
        
        String content = srcCM.getData().getOrDefault(CFG_MAP_FSOBJ_CONTENT_KEY, "");
        long size = Long.parseLong(srcCM.getMetadata().getLabels().getOrDefault(CFG_MAP_LABEL_FSOBJ_SIZE_KEY, "0"));
        executeCloudFunction(client -> createOrReplaceFSCM(client, 
                                                           target,
                                                           createOrReplaceParentDirFSCM(client, target, size),
                                                           Collections.singletonMap(CFG_MAP_FSOBJ_CONTENT_KEY, content),
                                                           false), 
                             KubernetesClient.class);
    }

    @Override
    public void move(final Path source,
                     final Path target,
                     final CopyOption... options) throws DirectoryNotEmptyException, 
        AtomicMoveNotSupportedException, IOException, SecurityException {
        try {
            copy(source, target);
        } catch (Exception e) {
            try {
                delete(target);
            } catch (NoSuchFileException nsfe) {
                throw new IOException("Moving file failed due to Copy Source Exception [" + e.getMessage() + "].");
            } catch (Exception exp) {
                throw new IOException("Moving file failed due to these errors: Copy Source Exception [" + 
                        e.getMessage() + "]; Delete Target Exception [" + exp.getMessage() + "].");
            }
        } 
        
        try {
            delete(source);
        } catch (Exception e) {
            throw new IOException("Moving file failed due to Delete Source Exception [" + e.getMessage() + "], " +
                    "which will leave file system in an inconsistent state.");
        }
    }
    
    @Override
    protected void checkFileNotExistThenThrow(final Path path, boolean isLink) 
            throws NoSuchFileException, NotLinkException {
        executeCloudFunction(client -> getFsObjCM(client, path), KubernetesClient.class)
            .orElseThrow(() -> new NoSuchFileException(path.toRealPath().toString()));
    }

    @Override
    protected void checkFileExistsThenThrow(final Path path) throws FileAlreadyExistsException {
        if (executeCloudFunction(client -> getFsObjCM(client, path), KubernetesClient.class).isPresent()) {
            throw new FileAlreadyExistsException(path.toRealPath().toString());
        }
    }
}