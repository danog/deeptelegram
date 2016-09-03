/*
 * Copyright (C) 2014 The Android Open Source Project
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
package it.deeptelegram.messenger.exoplayer.text.webvtt;

import it.deeptelegram.messenger.exoplayer.text.Cue;
import it.deeptelegram.messenger.exoplayer.text.Subtitle;
import it.deeptelegram.messenger.exoplayer.util.Assertions;

import java.util.Collections;
import java.util.List;

/**
 * Representation of a Webvtt subtitle embedded in a MP4 container file.
 */
/* package */ final class Mp4WebvttSubtitle implements Subtitle {

  private final List<Cue> cues;

  public Mp4WebvttSubtitle(List<Cue> cueList) {
    cues = Collections.unmodifiableList(cueList);
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {
    return timeUs < 0 ? 0 : -1;
  }

  @Override
  public int getEventTimeCount() {
    return 1;
  }

  @Override
  public long getEventTime(int index) {
    Assertions.checkArgument(index == 0);
    return 0;
  }

  @Override
  public long getLastEventTime() {
    return 0;
  }

  @Override
  public List<Cue> getCues(long timeUs) {
    return timeUs >= 0 ? cues : Collections.<Cue>emptyList();
  }
}
