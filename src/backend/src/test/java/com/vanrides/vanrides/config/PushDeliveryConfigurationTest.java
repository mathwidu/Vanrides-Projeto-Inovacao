package com.vanrides.vanrides.config;

import com.vanrides.vanrides.model.DeviceToken;
import com.vanrides.vanrides.model.NotificationEvent;
import com.vanrides.vanrides.service.NoopPushDeliveryGateway;
import com.vanrides.vanrides.service.PushDeliveryGateway;
import com.vanrides.vanrides.service.PushDeliveryResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PushDeliveryConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PushDeliveryConfiguration.class);

    @Test
    void shouldProvideNoopGatewayByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PushDeliveryGateway.class);
            NoopPushDeliveryGateway gateway = context.getBean(NoopPushDeliveryGateway.class);
            assertThat(context.getBean(PushDeliveryGateway.class)).isSameAs(gateway);
            assertThat(gateway.providerId()).isEqualTo("none");
            assertThat(gateway.isConfigured()).isFalse();
            assertThat(gateway.send(new DeviceToken(), new NotificationEvent()))
                    .isEqualTo(PushDeliveryResult.failed("provider_not_configured"));
        });
    }

    @Test
    void shouldProvideOnlyNoopGatewayWhenProviderIsExplicitlyNone() {
        contextRunner
                .withPropertyValues(PushDeliveryConfiguration.PROVIDER_PROPERTY + "=none")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PushDeliveryGateway.class);
                    assertThat(context).hasSingleBean(NoopPushDeliveryGateway.class);
                });
    }

    @Test
    void shouldNormalizeProviderSelectionCase() {
        contextRunner
                .withPropertyValues(PushDeliveryConfiguration.PROVIDER_PROPERTY + "=NONE")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PushDeliveryGateway.class);
                    assertThat(context).hasSingleBean(NoopPushDeliveryGateway.class);
                });
    }

    @Test
    void shouldUseExplicitProviderWithoutRegisteringNoop() {
        ConfiguredPushDeliveryGateway provider = new ConfiguredPushDeliveryGateway();

        contextRunner
                .withPropertyValues(PushDeliveryConfiguration.PROVIDER_PROPERTY + "=configured")
                .withBean(
                        "configuredPushDeliveryGateway",
                        PushDeliveryGateway.class,
                        () -> provider
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PushDeliveryGateway.class);
                    assertThat(context.getBean(PushDeliveryGateway.class)).isSameAs(provider);
                    assertThat(context).doesNotHaveBean(NoopPushDeliveryGateway.class);
                    assertThat(provider.providerId()).isEqualTo("configured");
                });
    }

    @Test
    void shouldUseSinglePrimaryExplicitProvider() {
        ConfiguredPushDeliveryGateway provider = new ConfiguredPushDeliveryGateway();

        contextRunner
                .withPropertyValues(PushDeliveryConfiguration.PROVIDER_PROPERTY + "=configured")
                .withBean(
                        "configuredPushDeliveryGateway",
                        PushDeliveryGateway.class,
                        () -> provider,
                        beanDefinition -> beanDefinition.setPrimary(true)
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PushDeliveryGateway.class);
                    assertThat(context.getBean(PushDeliveryGateway.class)).isSameAs(provider);
                    assertThat(context).doesNotHaveBean(NoopPushDeliveryGateway.class);
                });
    }

    @Test
    void shouldMapRuntimeEnvironmentAliasToSelectedProvider() {
        ConfiguredPushDeliveryGateway provider = new ConfiguredPushDeliveryGateway();

        contextRunner
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withSystemProperties("VANRIDES_PUSH_PROVIDER=configured")
                .withBean(
                        "configuredPushDeliveryGateway",
                        PushDeliveryGateway.class,
                        () -> provider
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PushDeliveryGateway.class);
                    assertThat(context.getBean(PushDeliveryGateway.class)).isSameAs(provider);
                    assertThat(context).doesNotHaveBean(NoopPushDeliveryGateway.class);
                });
    }

    @Test
    void shouldFailWhenConfiguredProviderHasNoAdapter() {
        contextRunner
                .withPropertyValues(PushDeliveryConfiguration.PROVIDER_PROPERTY + "=configured")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Exactly one PushDeliveryGateway must be registered; found 0.");
                });
    }

    @Test
    void shouldFailWhenAdapterIsRegisteredWithoutSelectingItsProvider() {
        contextRunner
                .withBean(
                        "configuredPushDeliveryGateway",
                        PushDeliveryGateway.class,
                        ConfiguredPushDeliveryGateway::new
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Exactly one PushDeliveryGateway must be registered; found 2.");
                });
    }

    @Test
    void shouldFailWhenPrimaryAdapterIsRegisteredWithoutSelectingItsProvider() {
        contextRunner
                .withBean(
                        "configuredPushDeliveryGateway",
                        PushDeliveryGateway.class,
                        ConfiguredPushDeliveryGateway::new,
                        beanDefinition -> beanDefinition.setPrimary(true)
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Exactly one PushDeliveryGateway must be registered; found 2.");
                });
    }

    @Test
    void shouldFailWhenConfiguredProviderHasMultipleAdapters() {
        contextRunner
                .withPropertyValues(PushDeliveryConfiguration.PROVIDER_PROPERTY + "=configured")
                .withBean(
                        "firstPushDeliveryGateway",
                        PushDeliveryGateway.class,
                        ConfiguredPushDeliveryGateway::new
                )
                .withBean(
                        "secondPushDeliveryGateway",
                        PushDeliveryGateway.class,
                        ConfiguredPushDeliveryGateway::new
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Exactly one PushDeliveryGateway must be registered; found 2.");
                });
    }

    @Test
    void shouldFailWhenConfiguredProviderHasMultipleAdaptersEvenWithPrimary() {
        contextRunner
                .withPropertyValues(PushDeliveryConfiguration.PROVIDER_PROPERTY + "=configured")
                .withBean(
                        "firstPushDeliveryGateway",
                        PushDeliveryGateway.class,
                        ConfiguredPushDeliveryGateway::new,
                        beanDefinition -> beanDefinition.setPrimary(true)
                )
                .withBean(
                        "secondPushDeliveryGateway",
                        PushDeliveryGateway.class,
                        ConfiguredPushDeliveryGateway::new
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Exactly one PushDeliveryGateway must be registered; found 2.");
                });
    }

    @Test
    void shouldFailWhenSelectedProviderDoesNotMatchAdapterIdentity() {
        contextRunner
                .withPropertyValues(PushDeliveryConfiguration.PROVIDER_PROPERTY + "=other")
                .withBean(
                        "configuredPushDeliveryGateway",
                        PushDeliveryGateway.class,
                        ConfiguredPushDeliveryGateway::new
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage(
                                    "The selected push provider must match the registered PushDeliveryGateway."
                            );
                });
    }

    @Test
    void shouldFailWhenPrimaryAdapterIdentityDoesNotMatchSelectedProvider() {
        contextRunner
                .withPropertyValues(PushDeliveryConfiguration.PROVIDER_PROPERTY + "=other")
                .withBean(
                        "configuredPushDeliveryGateway",
                        PushDeliveryGateway.class,
                        ConfiguredPushDeliveryGateway::new,
                        beanDefinition -> beanDefinition.setPrimary(true)
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage(
                                    "The selected push provider must match the registered PushDeliveryGateway."
                            );
                });
    }

    static final class ConfiguredPushDeliveryGateway implements PushDeliveryGateway {

        @Override
        public String providerId() {
            return "configured";
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public PushDeliveryResult send(DeviceToken deviceToken, NotificationEvent event) {
            return PushDeliveryResult.sent("configured");
        }
    }
}
