/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.connector;

import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.intellij.openapi.project.Project;
import com.microsoft.azure.toolkit.intellij.common.AzureFormJPanel;
import com.microsoft.azure.toolkit.lib.common.entity.IAzureResource;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jdom.Attribute;
import org.jdom.Element;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * the <b>{@code resource}</b> in <b>{@code resource connection}</b><br>
 * it's usually An Azure resource or an intellij module
 */
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AzureServiceResource<T extends IAzureResource<?>> implements Resource<T> {
    @Nonnull
    private final ResourceId id;
    @Getter
    @Nonnull
    private final AzureServiceResource.Definition<T> definition;
    private T data;

    public AzureServiceResource(T data, @Nonnull AzureServiceResource.Definition<T> definition) {
        this(data.id(), definition);
        this.data = data;
    }

    public AzureServiceResource(String id, @Nonnull AzureServiceResource.Definition<T> definition) {
        this.id = ResourceId.fromString(id);
        this.definition = definition;
    }

    public synchronized T getData() {
        if (Objects.isNull(this.data)) {
            this.data = this.definition.get.apply(this.id.id());
        }
        return this.data;
    }

    @Override
    @EqualsAndHashCode.Include
    public String getDataId() {
        return this.id.id();
    }

    @Override
    public String getName() {
        return this.id.name();
    }

    @Getter
    @RequiredArgsConstructor
    public static class Definition<T extends IAzureResource<?>> implements ResourceDefinition<T> {
        private final String name;
        private final String title;
        private final String icon;
        @Getter(AccessLevel.NONE)
        private final Supplier<AzureFormJPanel<T>> select;
        private final Function<String, T> get;

        @Override
        public Resource<T> define(T resource) {
            return new AzureServiceResource<>(resource, this);
        }

        public Resource<T> define(String dataId) {
            return new AzureServiceResource<>(dataId, this);
        }

        @Override
        public AzureFormJPanel<T> getResourcePanel(Project project) {
            return this.select.get();
        }

        @Override
        public boolean write(@Nonnull Element ele, @Nonnull Resource<T> resource) {
            ele.setAttribute(new Attribute("id", resource.getId()));
            ele.addContent(new Element("dataId").addContent(resource.getDataId()));
            return true;
        }

        @Override
        public Resource<T> read(@Nonnull Element ele) {
            final String id = ele.getChildTextTrim("dataId");
            return Optional.ofNullable(id).map(this::define).orElse(null);
        }

        @Override
        public String toString() {
            return this.getTitle();
        }
    }
}
