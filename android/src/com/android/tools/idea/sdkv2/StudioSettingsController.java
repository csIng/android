/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.idea.sdkv2;

import com.android.repository.api.RepoManager;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.meta.CommonFactory;
import com.intellij.openapi.components.*;
import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import org.jetbrains.annotations.Nullable;

/**
 * Controller class to get settings values using intellij persistent data mechanism.
 * Compare to {@link com.android.sdklib.internal.repository.updater.SettingsController}
 * which uses a file maintained separately.
 *
 * TODO: reevaluate the need for each setting after repo adoption is complete.
 */
@State(
  name = "StudioSettingsController",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/remotesdk.xml", roamingType = RoamingType.DISABLED)
  }
)
public class StudioSettingsController implements PersistentStateComponent<StudioSettingsController.PersistentState>, SettingsController {

  private PersistentState myState = new PersistentState();
  private String[] myValidChannels =
    ((CommonFactory)RepoManager.getCommonModule().createLatestFactory()).createRemotePackage().getValidChannels();

  @Override
  public boolean getForceHttp() {
    return myState.myForceHttp;
  }

  @Override
  public void setForceHttp(boolean forceHttp) {
    myState.myForceHttp = forceHttp;
  }

  @Override
  @Nullable
  public String getChannel() {
    // Studio channels are named like "Beta Channel", "Dev Channel", etc. Repo channels are named like
    // "10-beta", "20-dev", etc. We match them up based on the common parts of the names.
    String channel = ChannelStatus.fromCode(UpdateSettings.getInstance().getUpdateChannelType()).getDisplayName().toLowerCase();
    channel = channel.substring(0, channel.indexOf(' '));
    for (String repoChannel : myValidChannels) {
      if (repoChannel.endsWith("-" + channel)) {
        return repoChannel;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PersistentState getState() {
    return myState;
  }

  @Override
  public void loadState(PersistentState state) {
    myState = state;
  }

  public static SettingsController getInstance() {
    return ServiceManager.getService(StudioSettingsController.class);
  }

  public static class PersistentState {
    public boolean myForceHttp;
  }

  private StudioSettingsController() {}
}
