/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getTestCaseName
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.isNull
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import javax.swing.DefaultRowSorter
import javax.swing.RowSorter
import javax.swing.SortOrder
import java.io.File

/**
 * Unit tests for [AndroidTestResultsTableView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class AndroidTestResultsTableViewTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)

  @Mock
  lateinit var mockListener: AndroidTestResultsTableListener

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun initialTable() {
    val table = AndroidTestResultsTableView(mockListener)

    // Assert columns.
    assertThat(table.getModelForTesting().columnInfos).hasLength(2)
    assertThat(table.getModelForTesting().columnInfos[0].name).isEqualTo("Tests")
    assertThat(table.getModelForTesting().columnInfos[1].name).isEqualTo("Status")

    // Assert rows.
    assertThat(table.getModelForTesting().rowCount).isEqualTo(0)
  }

  @Test
  fun addDevice() {
    val table = AndroidTestResultsTableView(mockListener)

    table.addDevice(device("deviceId1", "deviceName1"))
    table.addDevice(device("deviceId2", "deviceName2"))

    assertThat(table.getModelForTesting().columnInfos).hasLength(4)
    assertThat(table.getModelForTesting().columnInfos[2].name).isEqualTo("deviceName1")
    assertThat(table.getModelForTesting().columnInfos[3].name).isEqualTo("deviceName2")

    // Row count should be still zero until any test results come in.
    assertThat(table.getModelForTesting().rowCount).isEqualTo(0)
  }

  @Test
  fun addTestResults() {
    val table = AndroidTestResultsTableView(mockListener)
    val device1 = device("deviceId1", "deviceName1")
    val device2 = device("deviceId2", "deviceName2")
    val testcase1OnDevice1 = AndroidTestCase("testid1", "method1", "class1", "package1")
    val testcase2OnDevice1 = AndroidTestCase("testid2", "method2", "class2", "package2")
    val testcase1OnDevice2 = AndroidTestCase("testid1", "method1", "class1", "package1")

    table.addDevice(device1)
    table.addDevice(device2)
    table.addTestCase(device1, testcase1OnDevice1)
    table.addTestCase(device1, testcase2OnDevice1)
    table.addTestCase(device2, testcase1OnDevice2)

    // No test cases are finished yet.
    assertThat(table.getModelForTesting().rowCount).isEqualTo(2)
    assertThat(table.getModelForTesting().getItem(0).getTestCaseName()).isEqualTo("class1.method1")
    assertThat(table.getModelForTesting().getItem(0).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.SCHEDULED)
    assertThat(table.getModelForTesting().getItem(0).getTestCaseResult(device2)).isEqualTo(AndroidTestCaseResult.SCHEDULED)
    assertThat(table.getModelForTesting().getItem(1).getTestCaseName()).isEqualTo("class2.method2")
    assertThat(table.getModelForTesting().getItem(1).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.SCHEDULED)
    assertThat(table.getModelForTesting().getItem(1).getTestCaseResult(device2)).isNull()

    // Let test case 1 and 2 finish on the device 1.
    testcase1OnDevice1.result = AndroidTestCaseResult.PASSED
    testcase2OnDevice1.result = AndroidTestCaseResult.FAILED

    assertThat(table.getModelForTesting().getItem(0).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.PASSED)
    assertThat(table.getModelForTesting().getItem(0).getTestCaseResult(device2)).isEqualTo(AndroidTestCaseResult.SCHEDULED)
    assertThat(table.getModelForTesting().getItem(1).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.FAILED)
    assertThat(table.getModelForTesting().getItem(1).getTestCaseResult(device2)).isNull()

    // Let test case 1 finish on the device 2.
    testcase1OnDevice2.result = AndroidTestCaseResult.PASSED

    assertThat(table.getModelForTesting().getItem(0).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.PASSED)
    assertThat(table.getModelForTesting().getItem(0).getTestCaseResult(device2)).isEqualTo(AndroidTestCaseResult.PASSED)
    assertThat(table.getModelForTesting().getItem(1).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.FAILED)
    assertThat(table.getModelForTesting().getItem(1).getTestCaseResult(device2)).isNull()
  }

  @Test
  fun addTestResultsWithRetention() {
    val table = AndroidTestResultsTableView(mockListener)
    val device1 = device("deviceId1", "deviceName1")
    val retentionFilePath = "foo"
    val testcase1OnDevice1 = AndroidTestCase(id = "testid1",
                                             methodName = "method1",
                                             className = "class1",
                                             packageName = "package1",
                                             result = AndroidTestCaseResult.FAILED,
                                             retentionSnapshot = File(retentionFilePath))

    table.addDevice(device1)
    table.addTestCase(device1, testcase1OnDevice1)

    assertThat(table.getModelForTesting().getItem(0).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.FAILED)
    assertThat(table.getModelForTesting().getItem(0).getRetentionSnapshot(device1)!!.path).isEqualTo(retentionFilePath)
  }

  @Test
  fun clickTestResultsRow() {
    val table = AndroidTestResultsTableView(mockListener)
    val device1 = device("deviceId1", "deviceName1")
    val device2 = device("deviceId2", "deviceName2")

    table.addDevice(device1)
    table.addDevice(device2)
    table.addTestCase(device1, AndroidTestCase("testid1", "method1", "class1", "package1", AndroidTestCaseResult.PASSED, "test logcat message"))
    table.addTestCase(device1, AndroidTestCase("testid2", "method2", "class2", "package2", AndroidTestCaseResult.FAILED))
    table.addTestCase(device2, AndroidTestCase("testid1", "method1", "class1", "package1", AndroidTestCaseResult.SKIPPED))
    table.addTestCase(device2, AndroidTestCase("testid2", "method2", "class2", "package2", AndroidTestCaseResult.SKIPPED))

    // Select the test case 1. Click on the test name column.
    table.getTableViewForTesting().setColumnSelectionInterval(0, 0)
    table.getTableViewForTesting().selectionModel.setSelectionInterval(0, 0)

    verify(mockListener).onAndroidTestResultsRowSelected(argThat { results ->
      results.getTestCaseName() == "class1.method1" &&
      results.getTestCaseResult(device1) == AndroidTestCaseResult.PASSED &&
      results.getLogcat(device1) == "test logcat message" &&
      results.getTestCaseResult(device2) == AndroidTestCaseResult.SKIPPED
    }, isNull())

    // Select the test case 2. Click on the device2 column.
    table.getTableViewForTesting().setColumnSelectionInterval(3, 3)
    table.getTableViewForTesting().selectionModel.setSelectionInterval(1, 1)

    verify(mockListener).onAndroidTestResultsRowSelected(argThat { results ->
      results.getTestCaseName() == "class2.method2" &&
      results.getTestCaseResult(device1) == AndroidTestCaseResult.FAILED &&
      results.getTestCaseResult(device2) == AndroidTestCaseResult.SKIPPED
    }, eq(device2))

    // Selecting the test case 2 again after clearing the selection should trigger the callback.
    // (Because a user may click the same row again after he/she closes the second page.)
    table.clearSelection()
    table.getTableViewForTesting().setColumnSelectionInterval(3, 3)
    table.getTableViewForTesting().selectionModel.setSelectionInterval(1, 1)

    verify(mockListener, times(2)).onAndroidTestResultsRowSelected(argThat { results ->
      results.getTestCaseName() == "class2.method2" &&
      results.getTestCaseResult(device1) == AndroidTestCaseResult.FAILED &&
      results.getTestCaseResult(device2) == AndroidTestCaseResult.SKIPPED
    }, eq(device2))
  }

  @Test
  fun setRowFilter() {
    val table = AndroidTestResultsTableView(mockListener)
    val device = device("deviceId", "deviceName")
    val testcase1 = AndroidTestCase("testid1", "method1", "class1", "package1")
    val testcase2 = AndroidTestCase("testid2", "method2", "class2", "package2")

    table.addDevice(device)
    table.addTestCase(device, testcase1)
    table.addTestCase(device, testcase2)
    table.setRowFilter { results ->
      results.getTestCaseName() == "class2.method2"
    }

    val view = table.getTableViewForTesting()
    assertThat(view.rowCount).isEqualTo(1)
    assertThat(view.convertRowIndexToView(0)).isEqualTo(-1)
    assertThat(view.convertRowIndexToView(1)).isEqualTo(0)
  }

  @Test
  fun setColumnFilter() {
    val table = AndroidTestResultsTableView(mockListener)
    val device1 = device("deviceId1", "deviceName1")
    val device2 = device("deviceId2", "deviceName2")

    table.addDevice(device1)
    table.addDevice(device2)
    table.setColumnFilter { device ->
      device.id == "deviceId2"
    }

    // "Test Name" + "Test Summary" + "Device 1" + "Device 2".
    // Device 1 is still visible but we change its width to 1px because
    // column sorter is not natively supported unlike rows.
    val view = table.getTableViewForTesting()
    val model = table.getModelForTesting()
    assertThat(view.columnCount).isEqualTo(4)
    assertThat(model.columnInfos[2].getWidth(view)).isEqualTo(1)
    assertThat(model.columnInfos[3].getWidth(view)).isEqualTo(120)
  }

  @Test
  fun sortRows() {
    val table = AndroidTestResultsTableView(mockListener)
    val device1 = device("deviceId1", "deviceName1")
    val device2 = device("deviceId2", "deviceName2")

    table.addDevice(device1)
    table.addDevice(device2)
    table.addTestCase(device1,
                      AndroidTestCase("testid1", "Z_method1", "Z_class1", "package1", AndroidTestCaseResult.SKIPPED))
    table.addTestCase(device1,
                      AndroidTestCase("testid2", "A_method2", "A_class2", "package2", AndroidTestCaseResult.PASSED))
    table.addTestCase(device2,
                      AndroidTestCase("testid1", "Z_method1", "Z_class1", "package1", AndroidTestCaseResult.FAILED))
    table.addTestCase(device2,
                      AndroidTestCase("testid2", "A_method2", "A_class2", "package2", AndroidTestCaseResult.PASSED))

    val view = table.getTableViewForTesting()
    assertThat(view.rowCount).isEqualTo(2)
    assertThat(view.rowSorter).isInstanceOf(DefaultRowSorter::class.java)
    val sorter = view.rowSorter as DefaultRowSorter<*, *>
    assertThat(sorter.isSortable(0)).isTrue()
    assertThat(sorter.isSortable(1)).isTrue()
    assertThat(sorter.isSortable(2)).isTrue()
    assertThat(sorter.isSortable(3)).isTrue()

    // Initially, rows are sorted in insertion order.
    assertThat(view.convertRowIndexToView(0)).isEqualTo(0)
    assertThat(view.convertRowIndexToView(1)).isEqualTo(1)

    // Sort by test name.
    sorter.sortKeys = listOf(RowSorter.SortKey(0, SortOrder.ASCENDING))
    assertThat(view.convertRowIndexToView(0)).isEqualTo(1)
    assertThat(view.convertRowIndexToView(1)).isEqualTo(0)

    // Sort by test summary status.
    sorter.sortKeys = listOf(RowSorter.SortKey(1, SortOrder.ASCENDING))
    assertThat(view.convertRowIndexToView(0)).isEqualTo(0)
    assertThat(view.convertRowIndexToView(1)).isEqualTo(1)

    // Sort by device1 test status.
    sorter.sortKeys = listOf(RowSorter.SortKey(2, SortOrder.ASCENDING))
    assertThat(view.convertRowIndexToView(0)).isEqualTo(1)
    assertThat(view.convertRowIndexToView(1)).isEqualTo(0)

    // Sort by device2 test status.
    sorter.sortKeys = listOf(RowSorter.SortKey(3, SortOrder.ASCENDING))
    assertThat(view.convertRowIndexToView(0)).isEqualTo(0)
    assertThat(view.convertRowIndexToView(1)).isEqualTo(1)
  }

  @Test
  fun tableShouldRetainSelectionAfterDataIsUpdated() {
    val table = AndroidTestResultsTableView(mockListener)
    val device1 = device("deviceId1", "deviceName1")
    val device2 = device("deviceId2", "deviceName2")

    table.addDevice(device1)
    table.addTestCase(device1, AndroidTestCase("testid1", "method1", "class1", "package1", AndroidTestCaseResult.PASSED, "test logcat message"))

    // Select the test case 1.
    table.getTableViewForTesting().setColumnSelectionInterval(0, 0)
    table.getTableViewForTesting().selectionModel.setSelectionInterval(0, 0)

    // Then, the test case 2 is added to the table.
    table.addTestCase(device1, AndroidTestCase("testid2", "method2", "class2", "package2", AndroidTestCaseResult.FAILED))

    // Make sure the test method1 is still being selected.
    assertThat(table.getTableViewForTesting().selectedObject?.methodName).isEqualTo("method1")

    // Next, we add a new device.
    table.addDevice(device2)
    table.addTestCase(device2, AndroidTestCase("testid1", "method1", "class1", "package1", AndroidTestCaseResult.SKIPPED))
    table.addTestCase(device2, AndroidTestCase("testid2", "method2", "class2", "package2", AndroidTestCaseResult.SKIPPED))

    // Again, make sure the test method1 is still being selected.
    assertThat(table.getTableViewForTesting().selectedObject?.methodName).isEqualTo("method1")
  }

  // Workaround for Kotlin nullability check.
  // ArgumentMatchers.argThat returns null for interface types.
  private fun argThat(matcher: (AndroidTestResults) -> Boolean): AndroidTestResults {
    ArgumentMatchers.argThat(matcher)
    return object : AndroidTestResults {
      override val methodName: String = ""
      override val className: String = ""
      override val packageName: String = ""
      override fun getTestCaseResult(device: AndroidDevice): AndroidTestCaseResult? = null
      override fun getTestResultSummary(): AndroidTestCaseResult = AndroidTestCaseResult.SCHEDULED
      override fun getLogcat(device: AndroidDevice): String = ""
      override fun getErrorStackTrace(device: AndroidDevice): String = ""
      override fun getBenchmark(device: AndroidDevice): String = ""
      override fun getRetentionSnapshot(device: AndroidDevice): File? = null
    }
  }

  private fun device(id: String, name: String): AndroidDevice {
    return AndroidDevice(id, name, AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(28))
  }
}