/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.SdkConstants.FN_SETTINGS_GRADLE
import com.android.testutils.TestUtils.runningFromBazel
import com.android.tools.idea.Projects.getBaseDirPath
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.FileSubject
import com.android.tools.idea.testing.FileSubject.file
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.TestProjectPaths.BASIC
import com.android.tools.idea.testing.TestProjectPaths.CENTRAL_BUILD_DIRECTORY
import com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI
import com.android.tools.idea.testing.TestProjectPaths.KOTLIN_GRADLE_DSL
import com.android.tools.idea.testing.TestProjectPaths.NESTED_MODULE
import com.android.tools.idea.testing.TestProjectPaths.PSD_DEPENDENCY
import com.android.tools.idea.testing.TestProjectPaths.PSD_SAMPLE
import com.android.tools.idea.testing.TestProjectPaths.PURE_JAVA_PROJECT
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.delete
import com.intellij.openapi.util.io.FileUtil.join
import com.intellij.openapi.util.io.FileUtil.writeToFile
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.Ignore
import java.io.File

/**
 * Snapshot tests for 'Gradle Sync'.
 *
 * These tests compare the results of sync by converting the resulting project to a stable text format which does not depend on local
 * environment (and ideally should not depend on the versions of irrelevant libraries) and comparing them to pre-recorded golden sync
 * results.
 *
 * The pre-recorded sync results can be found in testData/syncedProjectSnapshots/ *.txt files. Consult [snapshotSuffixes] for more
 * details on the way in which the file names are constructued.
 *
 * NOTE: It you made changes to sync or the test propjects which make these tests fail in an expected way, you can re-run the tests
 *       from IDE with -DUPDATE_SYNC_TEST_SNAPSHOTS to update the files.
 */
abstract class GradleSyncProjectComparisonTest(
    private val useNewSync: Boolean,
    private val singleVariantSync: Boolean = false
) : GradleSyncIntegrationTestCase() {
  override fun useNewSyncInfrastructure(): Boolean = useNewSync
  override fun useSingleVariantSyncInfrastructure(): Boolean = singleVariantSync
  override fun useCompoundSyncInfrastructure(): Boolean = false

  class NewSyncGradleSyncProjectComparisonTest : GradleSyncProjectComparisonTest(useNewSync = true)

  class NewSyncSingleVariantGradleSyncProjectComparisonTest :
      GradleSyncProjectComparisonTest(useNewSync = true, singleVariantSync = true) {
    @Ignore("b/124504437")
    override fun testNdkProjectSync() = Unit
  }

  class OldSyncGradleSyncProjectComparisonTest : GradleSyncProjectComparisonTest(useNewSync = false) {
    @Ignore("b/124497021")
    override fun testLoadPlainJavaProject() = Unit

    @Ignore("b/124508973")
    override fun testPsdDependency() = Unit

    @Ignore("b/124508973")
    override fun testPsdDependencyUpgradeLibraryModule() = Unit
  }

  private val snapshotSuffixes = listOfNotNull(
      // Suffixes to use to override the default expected result.
      ".new_sync.single_variant".takeIf { useNewSync && singleVariantSync },
      ".new_sync".takeIf { useNewSync },
      ".old_sync".takeIf { !useNewSync },
      ""
  )

  private lateinit var ideComponents: IdeComponents

  private fun importSyncAndDumpProject(projectDir: String, patch: ((projectRootPath: File) -> Unit)? = null): String {
    val projectRootPath = prepareProjectForImport(projectDir)
    patch?.invoke(projectRootPath)
    val project = this.project
    importProject(project.name, getBaseDirPath(project), null)
    return buildDump {
      dump(project)
    }
  }

  private fun syncAndDumpProject(): String {
    requestSyncAndWait()
    return buildDump {
      dump(project)
    }
  }

  override fun setUp() {
    super.setUp()
    val project = project
    ideComponents = IdeComponents(project)
    val projectSettings = GradleProjectSettings()
    projectSettings.distributionType = DEFAULT_WRAPPED
    GradleSettings.getInstance(project).linkedProjectsSettings = listOf(projectSettings)
  }

  // https://code.google.com/p/android/issues/detail?id=233038
  open fun testLoadPlainJavaProject() {
    val text = importSyncAndDumpProject(PURE_JAVA_PROJECT)
    assertIsEqualToSnapshot(text)
  }

  // See https://code.google.com/p/android/issues/detail?id=226802
  fun testNestedModule() {
    val text = importSyncAndDumpProject(NESTED_MODULE)
    assertIsEqualToSnapshot(text)
  }

  // See https://code.google.com/p/android/issues/detail?id=224985
  open fun testNdkProjectSync() {
    val text = importSyncAndDumpProject(HELLO_JNI)
    assertIsEqualToSnapshot(text)
  }

  // See https://code.google.com/p/android/issues/detail?id=76444
  fun testWithEmptyGradleSettingsFileInSingleModuleProject() {
    val text = importSyncAndDumpProject(BASIC) { createEmptyGradleSettingsFile() }
    assertIsEqualToSnapshot(text)
  }

  fun testTransitiveDependencies() {
    // TODO(b/124505053): Remove almost identical snapshots when SDK naming is fixed.
    val text = importSyncAndDumpProject(TRANSITIVE_DEPENDENCIES)
    assertIsEqualToSnapshot(text)
  }

  fun testSimpleApplication() {
    val text = importSyncAndDumpProject(SIMPLE_APPLICATION)
    assertIsEqualToSnapshot(text)
  }

  // See https://code.google.com/p/android/issues/detail?id=74259
  fun testWithCentralBuildDirectoryInRootModuleDeleted() {
    val text = importSyncAndDumpProject(CENTRAL_BUILD_DIRECTORY) { projectRootPath ->
      // The bug appears only when the central build folder does not exist.
      val centralBuildDirPath = File(projectRootPath, join("central", "build"))
      val centralBuildParentDirPath = centralBuildDirPath.parentFile
      delete(centralBuildParentDirPath)
    }
    assertIsEqualToSnapshot(text)
  }

  fun testSyncWithKotlinDsl() {
    val text = importSyncAndDumpProject(KOTLIN_GRADLE_DSL)
    assertIsEqualToSnapshot(text)
  }

  open fun testPsdDependency() {
    val text = importSyncAndDumpProject(PSD_DEPENDENCY) { projectRoot ->
      val localRepositories = AndroidGradleTests.getLocalRepositoriesForGroovy()
      val testRepositoryPath =
          File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectPaths.PSD_SAMPLE_REPO)).absolutePath!!
      val repositories = """
      maven {
        name "test"
        url "file:$testRepositoryPath"
      }
      $localRepositories
      """
      AndroidGradleTests.updateGradleVersionsAndRepositories(projectRoot, repositories, null)
    }
    assertIsEqualToSnapshot(text)
    val secondSync = syncAndDumpProject()
    // TODO(b/124677413): When fixed, [secondSync] should match the same snapshot. (Remove ".second_sync")
    assertIsEqualToSnapshot(secondSync, ".second_sync")
  }

  open fun testPsdDependencyDeleteModule() {
    val text = importSyncAndDumpProject(PSD_DEPENDENCY) { projectRoot ->
      val localRepositories = AndroidGradleTests.getLocalRepositoriesForGroovy()
      val testRepositoryPath =
          File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectPaths.PSD_SAMPLE_REPO)).absolutePath!!
      val repositories = """
      maven {
        name "test"
        url "file:$testRepositoryPath"
      }
      $localRepositories
      """
      AndroidGradleTests.updateGradleVersionsAndRepositories(projectRoot, repositories, null)
    }
    assertIsEqualToSnapshot(text, ".before_delete")
    PsProjectImpl(project).let { projectModel ->
      projectModel.removeModule(":moduleB")
      projectModel.applyChanges()
    }
    val textAfterDeleting = syncAndDumpProject()
    // TODO(b/124497021): Remove duplicate dependencies from the snapshot by reverting to the main snapshot when the bug is fixed.
    assertIsEqualToSnapshot(textAfterDeleting, ".after_moduleb_deleted")
  }

  open fun testPsdSample() {
    val text = importSyncAndDumpProject(PSD_SAMPLE)
    assertIsEqualToSnapshot(text)
  }

  open fun testPsdSampleRenamingModule() {
    val text = importSyncAndDumpProject(PSD_SAMPLE)
    assertIsEqualToSnapshot(text)
    WriteAction.run<Throwable> {
      project.baseDir.findFileByRelativePath("nested1")!!.rename("test", "container1")
    }
    PsProjectImpl(project).let { projectModel ->
      projectModel.removeModule(":nested1")
      projectModel.removeModule(":nested1:deep")
      with(projectModel.parsedModel.projectSettingsModel!!) {
        addModulePath(":container1")
        addModulePath(":container1:deep")
      }
      projectModel.applyChanges()
    }
    ApplicationManager.getApplication().saveAll()
    val textAfterDeleting = syncAndDumpProject()
    assertIsEqualToSnapshot(textAfterDeleting, ".after_rename")
  }

  open fun testPsdDependencyUpgradeLibraryModule() {
    val text = importSyncAndDumpProject(PSD_DEPENDENCY) { projectRoot ->
      val localRepositories = AndroidGradleTests.getLocalRepositoriesForGroovy()
      val testRepositoryPath =
          File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectPaths.PSD_SAMPLE_REPO)).absolutePath!!
      val repositories = """
      maven {
        name "test"
        url "file:$testRepositoryPath"
      }
      $localRepositories
      """
      AndroidGradleTests.updateGradleVersionsAndRepositories(projectRoot, repositories, null)
    }
    assertIsEqualToSnapshot(text, ".before_lib_upgrade")
    PsProjectImpl(project).let { projectModel ->
      projectModel
          .findModuleByGradlePath(":modulePlus")!!
          .dependencies
          .findLibraryDependencies("com.example.libs", "lib1")
          .single().version = "1.0".asParsed()
      projectModel
          .findModuleByGradlePath(":mainModule")!!
          .dependencies
          .findLibraryDependencies("com.example.libs", "lib1")
          .forEach { it.version = "0.9.1".asParsed() }
      projectModel
          .findModuleByGradlePath(":mainModule")!!
          .dependencies
          .findLibraryDependencies("com.example.jlib", "lib3")
          .single().version = "0.9.1".asParsed()
      projectModel.applyChanges()
    }
    val textAfterDeleting = syncAndDumpProject()
    // TODO(b/124677413): Remove irrelvant changes from the snapshot when the bug is fixed.
    assertIsEqualToSnapshot(textAfterDeleting, ".after_lib_upgrade")
  }

  private fun createEmptyGradleSettingsFile() {
    val settingsFilePath = File(projectFolderPath, FN_SETTINGS_GRADLE)
    assertTrue(delete(settingsFilePath))
    writeToFile(settingsFilePath, " ")
    assertAbout<FileSubject, File>(file()).that(settingsFilePath).isFile()
    refreshProjectFiles()
  }

  private fun getExpectedTextFor(project: String): String =
      getCandidateSnapshotFiles(project).let { candidateFiles ->
        candidateFiles.firstOrNull { it.exists() }?.let {
          println("Comparing with: ${it.relativeTo(File(AndroidTestBase.getTestDataPath()))}")
          it.readText().trimIndent()
        }
        ?: candidateFiles
            .joinToString(separator = "\n", prefix = "No snapshop files found. Candidates considered:\n\n") {
              it.relativeTo(File(AndroidTestBase.getTestDataPath())).toString()
            }
      }


  private fun getCandidateSnapshotFiles(project: String) = snapshotSuffixes
      .map { File("${AndroidTestBase.getTestDataPath()}/syncedProjectSnapshots/${project.substringAfter("projects/")}$it.txt") }


  private fun assertIsEqualToSnapshot(text: String, snapshotTestSuffix: String = "") {
    val fullSnapshotName = FileUtil.sanitizeFileName(getTestName(true)) + snapshotTestSuffix
    val expectedText = getExpectedTextFor(fullSnapshotName)

    if (System.getProperty("UPDATE_SYNC_TEST_SNAPSHOTS") != null) {
      updateSnapshotFile(fullSnapshotName, text)
    }

    if (runningFromBazel()) {
      // Produces diffs readable in logs.
      assertThat(text).isEqualTo(expectedText)
    }
    else {
      // Produces diffs that can be visually inspected in IDE.
      assertEquals(expectedText, text)
    }
  }

  private fun updateSnapshotFile(snapshotName: String, text: String) {
    getCandidateSnapshotFiles(snapshotName).let { candiates -> candiates.firstOrNull { it.exists() } ?: candiates.last() }.run {
      println("Writing to: ${this.absolutePath}")
      writeText(text)
    }
  }
}
