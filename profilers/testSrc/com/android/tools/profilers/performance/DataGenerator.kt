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
package com.android.tools.profilers.performance

import java.sql.Connection
import java.util.*

abstract class DataGenerator(protected val connection: Connection) {
  val random = Random(javaClass.name.hashCode().toLong())
  /**
   * Primary function responsible for populating the database. This function
   * will be called at each timestamp interval passing in a set of properties,
   * as well as the current timestamp. When implementing this function it is up
   * to the implementor to decide if each interval gets an associated data point or
   * if some data points are to be skipped.
   * All data generated from this function should as close as possible match real time
   * data generated by a device.
   */
  abstract fun generate(timestamp: Long, properties: GeneratorProperties)

  /**
   * Helper function that returns true if random number less than or equal to probability.
   */
  protected fun isWithinProbability(probability: Double): Boolean {
    return random.nextDouble() <= probability
  }
}
