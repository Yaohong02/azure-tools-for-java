package com.microsoft.azure.toolkit.intellij.appservice.task;

import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformUtils;
import com.microsoft.azure.toolkit.ide.appservice.function.FunctionAppActionsContributor;
import com.microsoft.azure.toolkit.ide.guidance.ComponentContext;
import com.microsoft.azure.toolkit.ide.guidance.Guidance;
import com.microsoft.azure.toolkit.ide.guidance.GuidanceTask;
import com.microsoft.azure.toolkit.intellij.common.action.IntellijAzureActionManager;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.entity.FunctionEntity;
import com.microsoft.azure.toolkit.lib.appservice.function.AzureFunctions;
import com.microsoft.azure.toolkit.lib.appservice.function.FunctionApp;
import com.microsoft.azure.toolkit.lib.common.action.Action;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import java.util.List;

public class TriggerFunctionTask implements GuidanceTask {
    public static final String FUNCTION_ID = "functionId";
    public static final String TRIGGER = "trigger";
    private final Project project;
    private final Guidance guidance;
    private final ComponentContext context;

    public TriggerFunctionTask(@Nonnull ComponentContext context) {
        this.context = context;
        this.project = context.getProject();
        this.guidance = context.getGuidance();
    }

    @Override
    public void execute() throws Exception {
        final String functionId = (String) context.getParameter(FUNCTION_ID);
        final String trigger = (String) context.getParameter(TRIGGER);
        final FunctionApp functionApp = Azure.az(AzureFunctions.class).functionApp(functionId);
        final List<FunctionEntity> functionEntities = functionApp.listFunctions(true);
        final FunctionEntity target = functionEntities.stream().filter(entity -> StringUtils.equals(entity.getName(), trigger))
                .findFirst().orElse(functionEntities.get(0));
        final Action.Id<FunctionEntity> action = PlatformUtils.isIdeaUltimate() ?
                FunctionAppActionsContributor.TRIGGER_FUNCTION_WITH_HTTP_CLIENT : FunctionAppActionsContributor.TRIGGER_FUNCTION_IN_BROWSER;
        IntellijAzureActionManager.getInstance().getAction(action).handle(target);
    }

    @Nonnull
    @Override
    public String getName() {
        return "task.function.trigger_function";
    }
}
