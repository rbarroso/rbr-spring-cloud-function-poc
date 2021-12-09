package org.springframework.cloud.stream.function;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.cloud.function.context.config.RoutingFunction;
import org.springframework.cloud.stream.config.BindingServiceConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
@AutoConfigureBefore({BindingServiceConfiguration.class})
@AutoConfigureAfter({ContextFunctionCatalogAutoConfiguration.class})
public class CustomFunctionConfiguration extends FunctionConfiguration {

  private static final String SOURCE_PROPERY = "spring.cloud.stream.source";

  @Override
  @Bean
  public InitializingBean functionBindingRegistrar(final Environment environment, final FunctionCatalog functionCatalog,
      final StreamFunctionProperties streamFunctionProperties) {
    return new CustomFunctionConfiguration.FunctionBindingRegistrar(functionCatalog, streamFunctionProperties);
  }

  /**
   * Creates and registers instances of BindableFunctionProxyFactory for each user defined function thus triggering destination bindings between function
   * arguments and destinations.
   * <p>
   * In other words this class is responsible to do the same work as EnableBinding except that it derives the input/output names from the names of the function
   * (e.g., function-in-0).
   */
  private static class FunctionBindingRegistrar implements InitializingBean, ApplicationContextAware, EnvironmentAware {

    protected final Log logger = LogFactory.getLog(getClass());

    private final FunctionCatalog functionCatalog;

    private final StreamFunctionProperties streamFunctionProperties;

    private ConfigurableApplicationContext applicationContext;

    private Environment environment;

    private int inputCount;

    private int outputCount;

    FunctionBindingRegistrar(final FunctionCatalog functionCatalog, final StreamFunctionProperties streamFunctionProperties) {
      this.functionCatalog = functionCatalog;
      this.streamFunctionProperties = streamFunctionProperties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
//      if (ObjectUtils.isEmpty(applicationContext.getBeanNamesForAnnotation(EnableBinding.class))) {
      this.determineFunctionName(functionCatalog, environment);
      final BeanDefinitionRegistry registry = (BeanDefinitionRegistry) applicationContext.getBeanFactory();

      if (StringUtils.hasText(streamFunctionProperties.getDefinition())) {
        final String[] functionDefinitions = this.filterEligibleFunctionDefinitions();
        for (final String functionDefinition : functionDefinitions) {
          final RootBeanDefinition functionBindableProxyDefinition = new RootBeanDefinition(BindableFunctionProxyFactory.class);
          final FunctionInvocationWrapper function = functionCatalog.lookup(functionDefinition);
          if (function != null) {
            final Type functionType = function.getFunctionType();
            if (function.isSupplier()) {
              this.inputCount = 0;
              this.outputCount = this.getOutputCount(functionType, true);
            } else if (function.isConsumer() || functionDefinition.equals(RoutingFunction.FUNCTION_NAME)) {
              this.inputCount = FunctionTypeUtils.getInputCount(functionType);
              this.outputCount = 0;
            } else {
              this.inputCount = FunctionTypeUtils.getInputCount(functionType);
              this.outputCount = this.getOutputCount(functionType, false);
            }

            functionBindableProxyDefinition.getConstructorArgumentValues().addGenericArgumentValue(functionDefinition);
            functionBindableProxyDefinition.getConstructorArgumentValues().addGenericArgumentValue(this.inputCount);
            functionBindableProxyDefinition.getConstructorArgumentValues().addGenericArgumentValue(this.outputCount);
            functionBindableProxyDefinition.getConstructorArgumentValues().addGenericArgumentValue(this.streamFunctionProperties);
            registry.registerBeanDefinition(functionDefinition + "_binding", functionBindableProxyDefinition);
          } else {
            logger.warn("The function definition '" + streamFunctionProperties.getDefinition() +
                "' is not valid. The referenced function bean or one of its components does not exist");
          }
        }
      }

      if (StringUtils.hasText(this.environment.getProperty(SOURCE_PROPERY))) {
        final String[] sourceNames = this.environment.getProperty(SOURCE_PROPERY).split(";");

        for (final String sourceName : sourceNames) {
          final FunctionInvocationWrapper sourceFunc = functionCatalog.lookup(sourceName);

          if (sourceFunc == null || //see https://github.com/spring-cloud/spring-cloud-stream/issues/2229
              (!sourceFunc.getFunctionDefinition().equals(sourceName) && applicationContext.containsBean(sourceName))) {
            final RootBeanDefinition functionBindableProxyDefinition = new RootBeanDefinition(BindableFunctionProxyFactory.class);
            functionBindableProxyDefinition.getConstructorArgumentValues().addGenericArgumentValue(sourceName);
            functionBindableProxyDefinition.getConstructorArgumentValues().addGenericArgumentValue(0);
            functionBindableProxyDefinition.getConstructorArgumentValues().addGenericArgumentValue(1);
            functionBindableProxyDefinition.getConstructorArgumentValues().addGenericArgumentValue(this.streamFunctionProperties);
            registry.registerBeanDefinition(sourceName + "_binding", functionBindableProxyDefinition);
          }
        }
      }
//      } else {
//        logger.info("Functional binding is disabled due to the presense of @EnableBinding annotation in your configuration");
//      }
    }

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
      this.applicationContext = (ConfigurableApplicationContext) applicationContext;
    }

    @Override
    public void setEnvironment(final Environment environment) {
      this.environment = environment;
    }

    private int getOutputCount(final Type functionType, final boolean isSupplier) {
      int outputCount = FunctionTypeUtils.getOutputCount(functionType);
      if (!isSupplier && functionType instanceof ParameterizedType) {
        final Type outputType = ((ParameterizedType) functionType).getActualTypeArguments()[1];
        if (FunctionTypeUtils.isMono(outputType) && outputType instanceof ParameterizedType
            && FunctionTypeUtils.getRawType(((ParameterizedType) outputType).getActualTypeArguments()[0]).equals(Void.class)) {
          outputCount = 0;
        } else if (FunctionTypeUtils.getRawType(outputType).equals(Void.class)) {
          outputCount = 0;
        }
      }
      return outputCount;
    }

    private boolean determineFunctionName(final FunctionCatalog catalog, final Environment environment) {
      final boolean autodetect = environment.getProperty("spring.cloud.stream.function.autodetect", boolean.class, true);
      String definition = streamFunctionProperties.getDefinition();
      if (!StringUtils.hasText(definition)) {
        definition = environment.getProperty("spring.cloud.function.definition");
      }

      if (StringUtils.hasText(definition)) {
        streamFunctionProperties.setDefinition(definition);
      } else if (Boolean.parseBoolean(environment.getProperty("spring.cloud.stream.function.routing.enabled", "false"))
          || environment.containsProperty("spring.cloud.function.routing-expression")) {
        streamFunctionProperties.setDefinition(RoutingFunction.FUNCTION_NAME);
      } else if (autodetect) {
        streamFunctionProperties.setDefinition(((FunctionInspector) functionCatalog).getName(functionCatalog.lookup("")));
      }
      return StringUtils.hasText(streamFunctionProperties.getDefinition());
    }

    /*
     * This is to accommodate Kafka streams binder, since it does not rely on binding mechanism provided by s-c-stream core.
     * So we basically filter out any function name who's type contains KTable or KStream.
     */
    private String[] filterEligibleFunctionDefinitions() {
      final List<String> eligibleFunctionDefinitions = new ArrayList<>();
      final String[] functionDefinitions = streamFunctionProperties.getDefinition().split(";");
      for (final String functionDefinition : functionDefinitions) {
        final String[] functionNames = StringUtils.delimitedListToStringArray(functionDefinition.replaceAll(",", "|").trim(), "|");
        boolean eligibleDefinition = true;
        for (int i = 0; i < functionNames.length && eligibleDefinition; i++) {
          final String functionName = functionNames[i];
          if (this.applicationContext.containsBean(functionName)) {
            final Object functionBean = this.applicationContext.getBean(functionName);
            final Type functionType = FunctionTypeUtils.discoverFunctionType(functionBean, functionName, (GenericApplicationContext) this.applicationContext);
            final String functionTypeStringValue = functionType.toString();
            if (functionTypeStringValue.contains("KTable") || functionTypeStringValue.contains("KStream")) {
              eligibleDefinition = false;
            }

          } else {
            logger.warn("You have defined function definition that does not exist: " + functionName);
          }
        }
        if (eligibleDefinition) {
          eligibleFunctionDefinitions.add(functionDefinition);
        }
      }
      return eligibleFunctionDefinitions.toArray(new String[0]);
    }
  }
}
