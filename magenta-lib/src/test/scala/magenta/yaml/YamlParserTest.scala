package magenta
package yaml

import org.json4s.jackson.JsonMethods._
import org.scalatest.{Matchers, FlatSpec}

class YamlParserTest extends FlatSpec with Matchers {
  "yaml parser" should "convert some yaml to json" in {
    pretty(render(YamlParser.toJson(
      """
        |Subnets:
        |  Description: The subnets where Janus will run
        |  Type: List<AWS::EC2::Subnet::Id>
        |VpcId:
        |  Description: The VPC in which Janus will run
        |  Type: AWS::EC2::VPC::Id
        |KeyName:
        |  Description: An ssh keypair to put on the instance
        |  Type: AWS::EC2::KeyPair::KeyName
      """.stripMargin
    ))) should be (
      """{
        |  "Subnets" : {
        |    "Description" : "The subnets where Janus will run",
        |    "Type" : "List<AWS::EC2::Subnet::Id>"
        |  },
        |  "VpcId" : {
        |    "Description" : "The VPC in which Janus will run",
        |    "Type" : "AWS::EC2::VPC::Id"
        |  },
        |  "KeyName" : {
        |    "Description" : "An ssh keypair to put on the instance",
        |    "Type" : "AWS::EC2::KeyPair::KeyName"
        |  }
        |}""".stripMargin
    )
  }

  "yaml parser" should "convert booleans" in {
    pretty(render(YamlParser.toJson(
      """
        |Test: true
      """.stripMargin
    ))) should be (
      """{
        |  "Test" : true
        |}""".stripMargin
    )
  }

  "yaml parser" should "convert integers" in {
    pretty(render(YamlParser.toJson(
      """
        |Test: 5
      """.stripMargin
    ))) should be (
      """{
        |  "Test" : 5
        |}""".stripMargin
    )
  }

  "yaml parser" should "convert numbers" in {
    pretty(render(YamlParser.toJson(
      """
        |Test: 5.2
      """.stripMargin
    ))) should be (
      """{
        |  "Test" : 5.2
        |}""".stripMargin
    )
  }

  "yaml parser" should "keeps dates as strings" in {
    pretty(render(YamlParser.toJson(
      """
        |Test: 2010-09-09
      """.stripMargin
    ))) should be (
      """{
        |  "Test" : "2010-09-09"
        |}""".stripMargin
    )
  }

  "yaml parser" should "convert lists" in {
    pretty(render(YamlParser.toJson(
      """
        |List:
        |  - Item 1
        |  - Item 2
        |  - Item 3
      """.stripMargin
    ))) should be (
      """{
        |  "List" : [ "Item 1", "Item 2", "Item 3" ]
        |}""".stripMargin
    )
  }

  "yaml parser" should "convert nested lists" in {
    pretty(render(YamlParser.toJson(
      """
        |List:
        |  - name: Item 1
        |    sublist:
        |      - a
        |      - b
        |      - c
        |  - Item 2
        |  - Item 3
      """.stripMargin
    ))) should be (
      """{
        |  "List" : [ {
        |    "name" : "Item 1",
        |    "sublist" : [ "a", "b", "c" ]
        |  }, "Item 2", "Item 3" ]
        |}""".stripMargin)
  }

  "yaml parser" should "convert nested maps" in {
    pretty(render(YamlParser.toJson(
      """
        |Map:
        |  Key: Value
      """.stripMargin
    ))) should be (
      """{
        |  "Map" : {
        |    "Key" : "Value"
        |  }
        |}""".stripMargin
    )
  }
}