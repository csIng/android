/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.res

import com.android.utils.concurrency.getAndUnwrap
import com.google.common.base.MoreObjects
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.impl.file.PsiPackageImpl
import org.jetbrains.android.augment.AndroidLightClassBase

/**
 * [PsiPackage] for packages that contain classes generated by aapt.
 *
 * @see AndroidLightClassBase
 * @see ProjectSystemPsiElementFinder
 * @see LightResourceClassService
 */
class AndroidLightPackage private constructor(
  manager: PsiManager,
  qualifiedName: String
) : PsiPackageImpl(manager, qualifiedName) {

  companion object {
    @JvmStatic
    fun withName(packageName: String, project: Project): PsiPackage {
      return ServiceManager.getService(project, InstanceCache::class.java).get(packageName)
    }
  }

  /**
   * Overrides [PsiPackageImpl.isValid] to ignore what files exist on disk. [AndroidLightPackage] instances are only used if the right
   * [PsiElementFinder] decided there are light R classes with this package name, so the package is valid even if there are no physical
   * files in corresponding directories.
   */
  override fun isValid(): Boolean {
    return project.isDisposed.not()
  }

  /**
   * Returns true if there are corresponding physical directories to navigate to.
   */
  override fun canNavigate(): Boolean {
    return super.isValid()
  }

  override fun toString(): String {
    return MoreObjects.toStringHelper(this).addValue(qualifiedName).toString()
  }

  /**
   * Project service responsible for interning instances of [AndroidLightPackage] with a given name.
   */
  class InstanceCache(private val psiManager: PsiManager) {
    /**
     * Cache of [PsiPackage] instances for a given package name.
     */
    private val packageCache: Cache<String, PsiPackage> = CacheBuilder.newBuilder().softValues().build()

    fun get(name: String): PsiPackage = packageCache.getAndUnwrap(name) { AndroidLightPackage(psiManager, name) }
  }
}
