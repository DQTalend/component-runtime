/**
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.server.service;

import static java.util.Locale.ROOT;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.talend.sdk.component.container.Container;
import org.talend.sdk.component.server.configuration.ComponentServerConfiguration;

import lombok.Data;

@ApplicationScoped
public class IconResolver {

    @Inject
    private ComponentServerConfiguration componentServerConfiguration;

    private boolean supportsSvg;

    private List<String> patterns;

    @PostConstruct
    protected void init() {
        supportsSvg = System.getProperty("talend.studio.version") == null
                && getExtensionPreferences().stream().anyMatch(it -> it.endsWith(".svg"));
        patterns = supportsSvg ? componentServerConfiguration.getIconExtensions()
                : componentServerConfiguration
                        .getIconExtensions()
                        .stream()
                        .filter(it -> !it.endsWith(".svg"))
                        .collect(toList());
    }

    protected Collection<String> getExtensionPreferences() {
        return componentServerConfiguration.getIconExtensions();
    }

    /**
     * IMPORTANT: the strategy moved to the configuration, high level we want something in this spirit:
     *
     * The look up strategy of an icon is the following one:
     * 1. Check in the server classpath in icons/override/${icon}_icon32.png
     * (optionally icons/override/${icon}.svg if in the preferences),
     * 2. Check in the family classloader the following names ${icon}_icon32.png, icons/${icon}_icon32.png, ...
     * 3. Check in the server classloader the following names ${icon}_icon32.png, icons/${icon}_icon32.png, ...
     *
     * This enable to
     * 1. override properly the icons (1),
     * 2. provide them in the family (2) and
     * 3. fallback on built-in icons if needed (3).
     *
     * @param container the component family container.
     * @param icon the icon to look up.
     * @return the icon if found.
     */
    public Icon resolve(final Container container, final String icon) {
        if (icon == null) {
            return null;
        }

        Cache cache = container.get(Cache.class);
        if (cache == null) {
            synchronized (container) {
                cache = container.get(Cache.class);
                if (cache == null) {
                    cache = new Cache();
                    container.set(Cache.class, cache);
                }
            }
        }
        final ClassLoader appLoader = Thread.currentThread().getContextClassLoader();
        return cache.icons
                .computeIfAbsent(icon,
                        k -> ofNullable(getOverridenIcon(icon, appLoader)
                                .orElseGet(() -> doLoad(container.getLoader(), icon)
                                        .orElseGet(() -> doLoad(appLoader, icon).orElse(null)))))
                .orElse(null);
    }

    private Optional<Icon> getOverridenIcon(final String icon, final ClassLoader appLoader) {
        Icon result = null;
        if (supportsSvg) {
            result = loadIcon(appLoader, "icons/override/" + icon + ".svg").orElse(null);
        }
        if (result == null) {
            return loadIcon(appLoader, "icons/override/" + icon + "_icon32.png");
        }
        return of(result);
    }

    public Optional<Icon> doLoad(final ClassLoader loader, final String icon) {
        return patterns
                .stream()
                .map(ext -> String.format(ext, icon))
                .map(path -> loadIcon(loader, path))
                .filter(Optional::isPresent)
                .findFirst()
                .flatMap(identity());
    }

    private Optional<Icon> loadIcon(final ClassLoader loader, final String path) {
        return ofNullable(loader.getResourceAsStream(path))
                .map(resource -> new Icon(getType(path.toLowerCase(ROOT)), toBytes(resource)));
    }

    private String getType(final String path) {
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        throw new IllegalArgumentException("Unsupported icon type: " + path);
    }

    private static class Cache {

        private final ConcurrentMap<String, Optional<Icon>> icons = new ConcurrentHashMap<>();
    }

    @Data
    public static class Icon {

        private final String type;

        private final byte[] bytes;
    }

    private byte[] toBytes(final InputStream resource) {
        try (final BufferedInputStream stream = new BufferedInputStream(resource)) {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(stream.available());
            final byte[] buffer = new byte[1024];
            int read;
            while ((read = stream.read(buffer, 0, buffer.length)) >= 0) {
                if (read > 0) {
                    byteArrayOutputStream.write(buffer, 0, read);
                }
            }
            return byteArrayOutputStream.toByteArray();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
