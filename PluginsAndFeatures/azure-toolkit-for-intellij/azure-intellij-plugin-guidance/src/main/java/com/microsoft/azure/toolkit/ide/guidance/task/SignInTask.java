package com.microsoft.azure.toolkit.ide.guidance.task;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.microsoft.azure.toolkit.ide.common.action.ResourceCommonActionsContributor;
import com.microsoft.azure.toolkit.ide.guidance.ComponentContext;
import com.microsoft.azure.toolkit.ide.guidance.Task;
import com.microsoft.azure.toolkit.intellij.common.action.IntellijAccountActionsContributor;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.common.action.Action;
import com.microsoft.azure.toolkit.lib.common.action.AzureActionManager;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azuretools.authmanage.AuthMethodManager;
import com.microsoft.azuretools.authmanage.models.AuthMethodDetails;
import com.microsoft.azuretools.sdkmanage.IdentityAzureManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import javax.annotation.Nonnull;
import java.util.Objects;

import static com.microsoft.azure.toolkit.lib.auth.model.AuthType.AZURE_CLI;

@RequiredArgsConstructor
public class SignInTask implements Task {
    public static final String SUBSCRIPTION_ID = "subscriptionId";

    @Nonnull
    private final ComponentContext context;

    @Override
    public void execute() {
        final AzureAccount az = Azure.az(AzureAccount.class);
        if (az.isSignedIn()) {
            // if already signed in, finish directly
            AzureMessager.getMessager().info(AzureString.format("Sign in successfully"));
        } else {
            final AuthMethodDetails methodDetails;
            if (isAzureCliAuthenticated()) {
                AzureMessager.getMessager().info("Signing in with Azure Cli...");
                methodDetails = IdentityAzureManager.getInstance().signInAzureCli().block();
            } else {
                AzureMessager.getMessager().info("Signing in with OAuth...");
                methodDetails = IdentityAzureManager.getInstance().signInOAuth().block();
            }
            if (!az.isSignedIn() || CollectionUtils.isEmpty(az.getSubscriptions())) {
                final Action<Object> signInAction = AzureActionManager.getInstance().getAction(Action.AUTHENTICATE);
                final Action<Object> tryAzureAction = AzureActionManager.getInstance().getAction(IntellijAccountActionsContributor.TRY_AZURE);
                throw new AzureToolkitRuntimeException("Failed to sign in or there is no subscription in your account", signInAction, tryAzureAction);
            } else {
                AuthMethodManager.getInstance().setAuthMethodDetails(methodDetails);
                AzureMessager.getMessager().info(AzureString.format("Sign in successfully with %s", Objects.requireNonNull(methodDetails).getAccountEmail()));
            }
        }
        final DataContext context = dataId -> CommonDataKeys.PROJECT.getName().equals(dataId) ? this.context.getProject() : null;
        final AnActionEvent event = AnActionEvent.createFromAnAction(new EmptyAction(), null, "azure.guidance.summary", context);
        AzureActionManager.getInstance().getAction(ResourceCommonActionsContributor.OPEN_AZURE_EXPLORER).handle(null, event);
    }

    private boolean isAzureCliAuthenticated() {
        return Azure.az(AzureAccount.class).accounts().stream()
            .filter(a -> a.getAuthType() == AZURE_CLI)
            .findFirst()
            .map(account -> {
                try {
                    return account.checkAvailable().block();
                } catch (final Exception e) {
                    return false;
                }
            })
            .orElse(false);
    }

    @Nonnull
    @Override
    public String getName() {
        return "task.auth.signin";
    }
}
