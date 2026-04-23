/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.plugin.ap;

import com.google.auto.service.AutoService;
import com.google.gson.Gson;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Annotation processor for Velocity.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({"com.velocitypowered.api.plugin.Plugin"})
public class PluginAnnotationProcessor extends AbstractProcessor {

  /**
   * Creates a new {@code PluginAnnotationProcessor}.
   *
   * <p>The processor is instantiated by the Java compiler and initialized via
   * {@link #init(ProcessingEnvironment)}.</p>
   */
  public PluginAnnotationProcessor() {
  }

  /**
   * The annotation processing environment.
   *
   * <p>Used to access utilities for messaging, file generation, and element inspection.</p>
   */
  private ProcessingEnvironment environment;

  /**
   * The fully qualified name of the plugin class discovered during processing.
   *
   * <p>Only one class annotated with {@link Plugin} is supported. If multiple are present,
   * a warning is issued and only the first is used.</p>
   */
  private String pluginClassFound;

  /**
   * Tracks whether a warning has already been issued for multiple {@link Plugin} annotations.
   */
  private boolean warnedAboutMultiplePlugins;

  /**
   * Initializes the annotation processor with the provided processing environment.
   *
   * @param processingEnv the annotation processing environment
   */
  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    this.environment = processingEnv;
  }

  /**
   * Returns the latest source version supported by this annotation processor.
   *
   * @return the latest supported source version
   */
  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /**
   * Processes the {@link Plugin} annotation to generate the {@code velocity-plugin.json} metadata file.
   *
   * <p>If multiple plugin classes are found, a warning is issued and only the first is used.</p>
   *
   * @param annotations the annotation types requested to be processed
   * @param roundEnv the environment for information about the current and prior round
   * @return {@code true} if the annotations are claimed by this processor, {@code false} otherwise
   */
  @Override
  public synchronized boolean process(Set<? extends TypeElement> annotations,
                                      RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      return false;
    }

    for (Element element : roundEnv.getElementsAnnotatedWith(Plugin.class)) {
      if (element.getKind() != ElementKind.CLASS) {
        environment.getMessager()
            .printMessage(Diagnostic.Kind.ERROR, "Only classes can be annotated with "
                + Plugin.class.getCanonicalName());
        return false;
      }

      Name qualifiedName = ((TypeElement) element).getQualifiedName();

      if (pluginClassFound != null) {
        if (!pluginClassFound.equals(qualifiedName.toString()) && !warnedAboutMultiplePlugins) {
          environment.getMessager()
              .printMessage(Diagnostic.Kind.WARNING, "Velocity does not yet currently support "
                  + "multiple plugins. We are using " + pluginClassFound
                  + " for your plugin's main class.");
          warnedAboutMultiplePlugins = true;
        }

        return false;
      }

      Plugin plugin = element.getAnnotation(Plugin.class);
      if (!SerializedPluginDescription.ID_PATTERN.matcher(plugin.id()).matches()) {
        environment.getMessager().printMessage(Diagnostic.Kind.ERROR, "Invalid ID for plugin "
            + qualifiedName
            + ". IDs must start alphabetically, have lowercase alphanumeric characters, and "
            + "can contain dashes or underscores.");
        return false;
      }

      for (Dependency dependency : plugin.dependencies()) {
        if (!SerializedPluginDescription.ID_PATTERN.matcher(dependency.id()).matches()) {
          environment.getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Invalid dependency ID '" + dependency.id() + "' for plugin " + qualifiedName
                  + ". IDs must start alphabetically, have lowercase alphanumeric characters, and "
                  + "can contain dashes or underscores.");
          return false;
        }
      }

      // All good, generate the velocity-plugin.json.
      SerializedPluginDescription description = SerializedPluginDescription
          .from(plugin, qualifiedName.toString());
      try {
        FileObject object = environment.getFiler()
            .createResource(StandardLocation.CLASS_OUTPUT, "", "velocity-plugin.json");
        try (Writer writer = new BufferedWriter(object.openWriter())) {
          new Gson().toJson(description, writer);
        }
        pluginClassFound = qualifiedName.toString();
      } catch (IOException e) {
        environment.getMessager()
            .printMessage(Diagnostic.Kind.ERROR, "Unable to generate plugin file");
      }
    }

    return false;
  }
}
