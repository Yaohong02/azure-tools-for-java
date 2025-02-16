/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.springcloud.component;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.ui.components.fields.ExtendableTextComponent.Extension;
import com.microsoft.azure.toolkit.intellij.common.AzureComboBox;
import com.microsoft.azure.toolkit.intellij.springcloud.creation.SpringCloudAppCreationDialog;
import com.microsoft.azure.toolkit.lib.common.cache.CacheManager;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.springcloud.SpringCloudApp;
import com.microsoft.azure.toolkit.lib.springcloud.SpringCloudAppDraft;
import com.microsoft.azure.toolkit.lib.springcloud.SpringCloudCluster;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class SpringCloudAppComboBox extends AzureComboBox<SpringCloudApp> {
    private SpringCloudCluster cluster;
    private final List<SpringCloudApp> localItems = new LinkedList<>();

    @Override
    protected String getItemText(final Object item) {
        if (Objects.isNull(item)) {
            return EMPTY_ITEM;
        }
        final SpringCloudApp app = (SpringCloudApp) item;
        if (!app.exists()) {
            return "(New) " + app.name();
        }
        return app.name();
    }

    public void setCluster(SpringCloudCluster cluster) {
        if (Objects.equals(cluster, this.cluster)) {
            return;
        }
        this.cluster = cluster;
        if (cluster == null) {
            this.clear();
            return;
        }
        this.reloadItems();
    }

    @Override
    public void setValue(SpringCloudApp val) {
        if (val.isDraftForCreating() && !this.localItems.contains(val)) {
            this.localItems.add(0, val);
        }
        this.reloadItems();
        super.setValue(val);
    }

    @Nullable
    @Override
    protected SpringCloudApp doGetDefaultValue() {
        return CacheManager.getUsageHistory(SpringCloudApp.class)
            .peek(v -> Objects.isNull(cluster) || Objects.equals(cluster, v.getParent()));
    }

    @NotNull
    @Override
    @AzureOperation(
        name = "springcloud.list_apps.cluster",
        params = {"this.cluster.name()"},
        type = AzureOperation.Type.SERVICE
    )
    protected List<? extends SpringCloudApp> loadItems() {
        final List<SpringCloudApp> apps = new ArrayList<>();
        if (Objects.nonNull(this.cluster)) {
            if (!this.localItems.isEmpty()) {
                apps.addAll(this.localItems.stream().filter(a -> a.getParent().getName().equals(this.cluster.getName())).collect(Collectors.toList()));
            }
            apps.addAll(cluster.apps().list());
        }
        return apps;
    }

    @Override
    protected void refreshItems() {
        Optional.ofNullable(this.cluster).ifPresent(c -> c.apps().refresh());
        super.refreshItems();
    }

    @Nonnull
    @Override
    protected List<Extension> getExtensions() {
        final List<Extension> extensions = super.getExtensions();
        final KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.ALT_DOWN_MASK);
        final String tooltip = String.format("Create Azure Spring App (%s)", KeymapUtil.getKeystrokeText(keyStroke));
        final Extension addEx = Extension.create(AllIcons.General.Add, tooltip, this::showAppCreationPopup);
        this.registerShortcut(keyStroke, addEx);
        extensions.add(addEx);
        return extensions;
    }

    private void showAppCreationPopup() {
        final SpringCloudAppCreationDialog dialog = new SpringCloudAppCreationDialog(this.cluster);
        dialog.setOkActionListener((config) -> {
            final SpringCloudAppDraft app = cluster.apps().create(config.getAppName(), cluster.getResourceGroupName());
            app.setConfig(config);
            dialog.close();
            this.setValue(app);
        });
        dialog.show();
    }
}
