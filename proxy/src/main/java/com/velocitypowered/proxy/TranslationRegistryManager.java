/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy;

import static java.util.function.Function.identity;

import com.velocityctd.proxy.util.ComponentUtils;
import com.velocitypowered.proxy.util.ClosestLocaleTranslator;
import com.velocitypowered.proxy.util.ResourceUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TranslationRegistryManager {

  private static final Logger LOGGER = LogManager.getLogger(TranslationRegistryManager.class);

  /**
   * The {@link Key} used to register Velocity's translation source in the Adventure global translator.
   */
  private final Key translationRegistryKey;

  TranslationRegistryManager(Key translationRegistryKey) {
    this.translationRegistryKey = translationRegistryKey;
  }

  TranslationRegistryManager() {
    this(Key.key("velocity", "translations"));
  }

  void unregisterTranslations() {
    for (Translator source : GlobalTranslator.translator().sources()) {
      if (source.name().equals(this.translationRegistryKey)) {
        GlobalTranslator.translator().removeSource(source);
      }
    }
  }

  void registerTranslations() {
    MiniMessageTranslationStore translationRegistry = MiniMessageTranslationStore.create(
        this.translationRegistryKey, ComponentUtils.parser().asMiniMessage());
    translationRegistry.defaultLocale(Locale.US);

    ClosestLocaleTranslator closestLocaleTranslator = new ClosestLocaleTranslator(translationRegistry);

    try {
      ResourceUtils.visitResources(VelocityServer.class, path -> {
        Path langPath = Path.of("lang");

        try {
          if (!Files.exists(langPath)) {
            Files.createDirectories(langPath);
          }

          try (Stream<Path> files = Files.walk(path)) {
            files.filter(Files::isRegularFile).forEach(src -> {
              Path target = langPath.resolve(src.getFileName().toString());
              if (Files.notExists(target)) {
                try {
                  saveMissingFile(src, target);
                } catch (IOException e) {
                  LOGGER.error("Failed copying translation file {}", target.getFileName(), e);
                }
              } else {
                try {
                  migrateIfNeeded(src, target);
                } catch (IOException e) {
                  LOGGER.error("Failed migrating translation file {}", target.getFileName(), e);
                }
              }
            });
          }

          try (Stream<Path> langFiles = Files.walk(langPath)) {
            langFiles.filter(Files::isRegularFile).forEach(file -> {
              try {
                registerTranslation(file, translationRegistry, closestLocaleTranslator);
              } catch (Exception e) {
                LOGGER.error("Failed registering translations from {}", file, e);
              }
            });
          }
        } catch (Exception e) {
          LOGGER.error("Encountered an error whilst loading translations", e);
        }
      }, "com", "velocitypowered", "proxy", "l10n");
    } catch (IOException e) {
      LOGGER.error("Encountered an I/O error whilst loading translations", e);
      return;
    }

    GlobalTranslator.translator().addSource(closestLocaleTranslator);
  }

  private void registerTranslation(Path file, MiniMessageTranslationStore translationRegistry,
                                   ClosestLocaleTranslator closestLocaleTranslator) throws IOException {
    String localePart = com.google.common.io.Files
        .getNameWithoutExtension(file.getFileName().toString());
    if (localePart.startsWith("messages")) {
      localePart = localePart.substring("messages".length());
    }

    if (localePart.startsWith("_")) {
      localePart = localePart.substring(1);
    }

    Locale locale = localePart.isBlank()
        ? Locale.US
        : Locale.forLanguageTag(localePart.replace('_', '-'));

    translationRegistry.registerAll(locale, file, false);
    closestLocaleTranslator.registerKnown(locale);
  }

  private void saveMissingFile(Path src, Path target) throws IOException {
    try (InputStream is = Files.newInputStream(src)) {
      Files.copy(is, target);
      LOGGER.info("Restored missing translation file {}", target.getFileName());
    }
  }

  private void migrateIfNeeded(Path src, Path target) throws IOException {
    Properties srcProperties = new Properties();
    try (InputStream is = Files.newInputStream(src)) {
      srcProperties.load(is);
    }

    Properties targetProperties = new Properties();
    try (InputStream is = Files.newInputStream(target)) {
      targetProperties.load(is);
    }

    Map<String, String> missingProperties = srcProperties.keySet()
        .stream()
        .filter(key -> !targetProperties.containsKey(key))
        .map(k -> (String) k)
        .collect(Collectors.toMap(identity(), srcProperties::getProperty));

    if (!missingProperties.isEmpty()) {
      migrate(target, missingProperties);
    }
  }

  private void migrate(Path target, Map<String, String> missingProperties) throws IOException {
    String timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z"));
    List<String> lines = Stream.concat(
        Stream.of("# Messages below have been added by a migration of this file at " + timestamp + "."),
        missingProperties.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + e.getValue())
    ).toList();

    Files.write(
        target,
        lines,
        StandardCharsets.ISO_8859_1, // Properties#load uses ISO 8859-1
        StandardOpenOption.APPEND
    );

    LOGGER.info("Migrated {} with a total of {} missing messages.", target.toString(), missingProperties.size());
  }
}
