/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.flint.spark

import org.opensearch.flint.spark.FlintSparkIndexOptions.OptionName.CHECKPOINT_LOCATION
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import org.apache.spark.FlintSuite
import org.apache.spark.sql.flint.config.FlintSparkConf

class FlintSparkIndexBuilderSuite extends FlintSuite {

  val indexName: String = "test_index"
  val testCheckpointLocation = "/test/checkpoints/"

  override def beforeAll(): Unit = {
    super.beforeAll()

    sql("""
        | CREATE TABLE spark_catalog.default.test
        | (
        |   name STRING,
        |   age INT,
        |   address STRUCT<first: STRING, second: STRUCT<city: STRING, street: STRING>>
        | )
        | USING JSON
      """.stripMargin)
  }

  protected override def afterAll(): Unit = {
    sql("DROP TABLE spark_catalog.default.test")

    super.afterAll()
  }

  test("indexOptions should not have checkpoint location when no conf") {
    assert(!conf.contains(FlintSparkConf.CHECKPOINT_LOCATION_ROOT_DIR.key))

    val options = FlintSparkIndexOptions(Map.empty)
    val builder = new FakeFlintSparkIndexBuilder

    val updatedOptions = builder.options(options, indexName).testOptions
    updatedOptions.checkpointLocation() shouldBe None
  }

  test("indexOptions should not override existing checkpoint location when no conf") {
    assert(!conf.contains(FlintSparkConf.CHECKPOINT_LOCATION_ROOT_DIR.key))

    val options =
      FlintSparkIndexOptions(Map(CHECKPOINT_LOCATION.toString -> testCheckpointLocation))
    val builder = new FakeFlintSparkIndexBuilder

    val updatedOptions = builder.options(options, indexName).testOptions
    updatedOptions.checkpointLocation() shouldBe Some(testCheckpointLocation)
  }

  test("indexOptions should not override existing checkpoint location with conf") {
    setFlintSparkConf(FlintSparkConf.CHECKPOINT_LOCATION_ROOT_DIR, testCheckpointLocation)
    assert(conf.contains(FlintSparkConf.CHECKPOINT_LOCATION_ROOT_DIR.key))

    val options =
      FlintSparkIndexOptions(Map(CHECKPOINT_LOCATION.toString -> testCheckpointLocation))
    val builder = new FakeFlintSparkIndexBuilder

    val updatedOptions = builder.options(options, indexName).testOptions
    updatedOptions.checkpointLocation() shouldBe Some(testCheckpointLocation)
  }

  test("indexOptions should have default checkpoint location with conf") {
    setFlintSparkConf(FlintSparkConf.CHECKPOINT_LOCATION_ROOT_DIR, testCheckpointLocation)
    assert(conf.contains(FlintSparkConf.CHECKPOINT_LOCATION_ROOT_DIR.key))

    val options = FlintSparkIndexOptions(Map.empty)
    val builder = new FakeFlintSparkIndexBuilder

    val updatedOptions = builder.options(options, indexName).testOptions
    assert(updatedOptions.checkpointLocation().isDefined, "Checkpoint location should be defined")
    assert(
      updatedOptions
        .checkpointLocation()
        .get
        .startsWith(s"${testCheckpointLocation}${indexName}"),
      s"Checkpoint location should start with ${testCheckpointLocation}${indexName}")
  }

  test("find column type") {
    builder()
      .onTable("test")
      .expectTableName("spark_catalog.default.test")
      .expectColumn("name", "string")
      .expectColumn("age", "int")
      .expectColumn("address", "struct<first:string,second:struct<city:string,street:string>>")
      .expectColumn("address.first", "string")
      .expectColumn("address.second", "struct<city:string,street:string>")
      .expectColumn("address.second.city", "string")
      .expectColumn("address.second.street", "string")
  }

  test("should qualify table name in default database") {
    builder()
      .onTable("test")
      .expectTableName("spark_catalog.default.test")

    builder()
      .onTable("default.test")
      .expectTableName("spark_catalog.default.test")

    builder()
      .onTable("spark_catalog.default.test")
      .expectTableName("spark_catalog.default.test")
  }

  test("should qualify table name and get columns in other database") {
    sql("CREATE DATABASE mydb")
    sql("CREATE TABLE mydb.test2 (address STRING) USING JSON")
    sql("USE mydb")

    try {
      builder()
        .onTable("test2")
        .expectTableName("spark_catalog.mydb.test2")

      builder()
        .onTable("mydb.test2")
        .expectTableName("spark_catalog.mydb.test2")

      builder()
        .onTable("spark_catalog.mydb.test2")
        .expectTableName("spark_catalog.mydb.test2")

      // Can parse any specified table name
      builder()
        .onTable("spark_catalog.default.test")
        .expectTableName("spark_catalog.default.test")
    } finally {
      sql("DROP DATABASE mydb CASCADE")
      sql("USE default")
    }
  }

  private def builder(): FakeFlintSparkIndexBuilder = {
    new FakeFlintSparkIndexBuilder
  }

  /**
   * Fake builder that have access to internal method for assertion
   */
  class FakeFlintSparkIndexBuilder extends FlintSparkIndexBuilder(new FlintSpark(spark)) {
    def testOptions: FlintSparkIndexOptions = this.indexOptions

    def onTable(tableName: String): FakeFlintSparkIndexBuilder = {
      this.tableName = tableName
      this
    }

    def expectTableName(expected: String): FakeFlintSparkIndexBuilder = {
      tableName shouldBe expected
      this
    }

    def expectColumn(expectName: String, expectType: String): FakeFlintSparkIndexBuilder = {
      val column = findColumn(expectName)
      column.name shouldBe expectName
      column.dataType shouldBe expectType
      this
    }

    override protected def buildIndex(): FlintSparkIndex = {
      null
    }
  }
}
