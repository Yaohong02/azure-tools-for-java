/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.arm;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.microsoft.azure.toolkit.ide.arm.DeploymentActionsContributor;
import com.microsoft.azure.toolkit.ide.common.IActionsContributor;
import com.microsoft.azure.toolkit.ide.common.action.ResourceCommonActionsContributor;
import com.microsoft.azure.toolkit.intellij.arm.action.DeploymentActions;
import com.microsoft.azure.toolkit.lib.common.action.AzureActionManager;
import com.microsoft.azure.toolkit.lib.resource.ResourceDeployment;
import com.microsoft.azure.toolkit.lib.resource.ResourceDeploymentModule;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public class IntellijDeploymentActionsContributor implements IActionsContributor {
    @Override
    public void registerHandlers(AzureActionManager am) {
        final BiPredicate<Object, AnActionEvent> createCondition = (r, e) -> r instanceof ResourceDeploymentModule;
        final BiConsumer<Object, AnActionEvent> createHandler = (c, e) ->
            DeploymentActions.createDeployment((Objects.requireNonNull(e.getProject())), ((ResourceDeploymentModule) c).getParent());
        am.registerHandler(ResourceCommonActionsContributor.CREATE, createCondition, createHandler);

        final BiPredicate<ResourceDeployment, AnActionEvent> editCondition = (r, e) -> r instanceof ResourceDeployment;
        final BiConsumer<ResourceDeployment, AnActionEvent> editHandler = (c, e) ->
            DeploymentActions.openTemplateView(Objects.requireNonNull(e.getProject()), c);
        am.registerHandler(DeploymentActionsContributor.EDIT, editCondition, editHandler);

        final BiPredicate<ResourceDeployment, AnActionEvent> updateCondition = (r, e) -> r instanceof ResourceDeployment;
        final BiConsumer<ResourceDeployment, AnActionEvent> updateHandler = (c, e) ->
            DeploymentActions.updateDeployment(Objects.requireNonNull(e.getProject()), c);
        am.registerHandler(DeploymentActionsContributor.UPDATE, updateCondition, updateHandler);

        final BiPredicate<ResourceDeployment, AnActionEvent> exportParameterCondition = (r, e) -> r instanceof ResourceDeployment;
        final BiConsumer<ResourceDeployment, AnActionEvent> exportParameterHandler = (c, e) ->
            DeploymentActions.exportParameters(Objects.requireNonNull(e.getProject()), c);
        am.registerHandler(DeploymentActionsContributor.EXPORT_PARAMETER, exportParameterCondition, exportParameterHandler);

        final BiPredicate<ResourceDeployment, AnActionEvent> exportTemplateCondition = (r, e) -> r instanceof ResourceDeployment;
        final BiConsumer<ResourceDeployment, AnActionEvent> exportTemplateHandler = (c, e) ->
            DeploymentActions.exportTemplate(Objects.requireNonNull(e.getProject()), c);
        am.registerHandler(DeploymentActionsContributor.EXPORT_TEMPLATE, exportTemplateCondition, exportTemplateHandler);
    }

    @Override
    public int getOrder() {
        return DeploymentActionsContributor.INITIALIZE_ORDER + 1;
    }
}
