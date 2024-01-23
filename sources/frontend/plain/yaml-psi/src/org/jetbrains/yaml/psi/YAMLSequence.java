/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.yaml.psi;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A collection representing a sequence of items
 */
public interface YAMLSequence extends YAMLCompoundValue {
  @NotNull
  List<YAMLSequenceItem> getItems();
}
