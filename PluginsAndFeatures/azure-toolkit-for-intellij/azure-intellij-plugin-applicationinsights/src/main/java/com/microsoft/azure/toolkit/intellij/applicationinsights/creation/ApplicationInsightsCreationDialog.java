/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.applicationinsights.creation;

import com.intellij.openapi.project.Project;
import com.microsoft.azure.toolkit.intellij.common.AzureDialog;
import com.microsoft.azure.toolkit.intellij.common.component.RegionComboBox;
import com.microsoft.azure.toolkit.intellij.common.component.SubscriptionComboBox;
import com.microsoft.azure.toolkit.intellij.common.component.resourcegroup.ResourceGroupComboBox;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.applicationinsights.ApplicationInsightDraft;
import com.microsoft.azure.toolkit.lib.applicationinsights.AzureApplicationInsights;
import com.microsoft.azure.toolkit.lib.common.form.AzureForm;
import com.microsoft.azure.toolkit.lib.common.form.AzureFormInput;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import com.microsoft.azure.toolkit.lib.common.task.AzureTaskManager;
import com.microsoft.azure.toolkit.lib.resource.ResourceGroup;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.event.ItemEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ApplicationInsightsCreationDialog extends AzureDialog<ApplicationInsightDraft> implements AzureForm<ApplicationInsightDraft> {
    private static final String DIALOG_TITLE = "Create Application Insights";

    private JPanel pnlRoot;
    private SubscriptionComboBox subscriptionComboBox;
    private ResourceGroupComboBox resourceGroupComboBox;
    private RegionComboBox regionComboBox;
    private InsightNameTextField txtName;

    public ApplicationInsightsCreationDialog(Project project) {
        super(project);
        init();
    }

    @Override
    protected void init() {
        super.init();
        this.subscriptionComboBox.setRequired(true);
        this.resourceGroupComboBox.setRequired(true);
        this.txtName.setRequired(true);
        this.regionComboBox.setRequired(true);
        this.subscriptionComboBox.addItemListener(this::onSubscriptionChanged);
    }

    @Override
    public AzureForm<ApplicationInsightDraft> getForm() {
        return this;
    }

    @Override
    protected String getDialogTitle() {
        return DIALOG_TITLE;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return pnlRoot;
    }

    @Override
    public ApplicationInsightDraft getValue() {
        final Subscription subscription = subscriptionComboBox.getValue();
        final ResourceGroup resourceGroup = resourceGroupComboBox.getValue();
        final String name = txtName.getValue();
        final Region region = regionComboBox.getValue();
        final String resourceGroupName = Optional.ofNullable(resourceGroup).map(ResourceGroup::getName).orElse(null);
        final ApplicationInsightDraft result =
                Azure.az(AzureApplicationInsights.class).forSubscription(subscription.getId()).applicationInsights().create(name, resourceGroupName);
        result.setRegion(region);
        return result;
    }

    @Override
    public void setValue(@Nonnull ApplicationInsightDraft data) {
        this.txtName.setValue(data.getName());
        this.subscriptionComboBox.setValue(data.getSubscription());
        // todo: @hanli refactor ai library to support get draft resource group
        AzureTaskManager.getInstance().runOnPooledThread(() -> {
            Optional.ofNullable(data.getResourceGroup()).ifPresent(resourceGroupComboBox::setValue);
            Optional.ofNullable(data.getRegion()).ifPresent(regionComboBox::setValue);
        });
    }

    @Override
    public List<AzureFormInput<?>> getInputs() {
        return Arrays.asList(txtName, subscriptionComboBox, resourceGroupComboBox, regionComboBox);
    }

    private void onSubscriptionChanged(final ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            final Subscription subscription = (Subscription) e.getItem();
            this.resourceGroupComboBox.setSubscription(subscription);
            this.regionComboBox.setSubscription(subscription);
            this.txtName.setSubscriptionId(subscription.getId());
        } else if (e.getStateChange() == ItemEvent.DESELECTED) {
            this.resourceGroupComboBox.setSubscription(null);
            this.regionComboBox.setSubscription(null);
        }
    }

    private void createUIComponents() {
        this.txtName = new InsightNameTextField();
        this.regionComboBox = new RegionComboBox() {
            protected List<? extends Region> loadItems() {
                if (Objects.nonNull(this.subscription)) {
                    return Azure.az(AzureApplicationInsights.class).forSubscription(subscription.getId()).listSupportedRegions();
                }
                return Collections.emptyList();
            }
        };
    }
}
