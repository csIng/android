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
package com.android.tools.idea.tests.gui.npw;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CreateNewProjectWithNoActivityTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void activityTemplate() {
    guiTest.welcomeFrame().createNewProject()
      .getChooseAndroidProjectStep()
      .chooseActivity("Add No Activity")
      .wizard()
      .clickNext()
      .getConfigureNewAndroidProjectStep()
      .enterName("NoActivityApp")
      .enterPackageName("dev.tools")
      .wizard()
      .clickFinish();

    // Verification
    String buildContent = guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("app/build.gradle")
      .getCurrentFileContents();

    assertThat(buildContent).contains("applicationId \"dev.tools\"");
    assertThat(buildContent).contains("implementation 'androidx.appcompat:appcompat:");
  }
}
