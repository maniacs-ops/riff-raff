package magenta
package yaml

import org.json4s.JsonAST._
import org.yaml.snakeyaml.{DumperOptions, Yaml}
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.resolver.Resolver
import java.lang.{Integer => JavaInteger, Boolean => JavaBoolean, Float => JavaFloat, Double => JavaDouble}
import java.util.{Map => JavaMap, ArrayList => JavaArrayList, Date => JavaDate}
import scala.collection.JavaConversions._

object YamlParser {

  def toJson(data: String): JValue = {
    val yaml = new Yaml(new Constructor(), new Representer(), new DumperOptions(), new CustomResolver())
    parse(yaml.load(data))
  }

  private def parse(obj: Object): JValue = obj match {
    case x: String => JString(x)
    case x: JavaInteger => JInt(x.toInt)
    case x: JavaBoolean => JBool(x)
    case x: JavaFloat => JDouble(x.toDouble)
    case x: JavaDouble => JDouble(x)
    case x: JavaDate => JString(x.toString)
    case x: JavaArrayList[_] => JArray(x.asInstanceOf[JavaArrayList[Object]].toList.map(parse))
    case x: JavaMap[_, _] => JObject(x.asInstanceOf[JavaMap[String, Object]].toList.map({ case (k, v) => JField(k, parse(v)) }))
  }

  private class CustomResolver extends Resolver {
    import Resolver._
    import org.yaml.snakeyaml.nodes.Tag

    override def addImplicitResolvers() {
      this.addImplicitResolver(Tag.BOOL, BOOL, "yYnNtTfFoO")
      this.addImplicitResolver(Tag.INT, INT, "-+0123456789")
      this.addImplicitResolver(Tag.FLOAT, FLOAT, "-+0123456789.")
      this.addImplicitResolver(Tag.MERGE, MERGE, "<")
      this.addImplicitResolver(Tag.NULL, NULL, "~nN\u0000")
      this.addImplicitResolver(Tag.NULL, EMPTY, null)
      this.addImplicitResolver(Tag.YAML, YAML, "!&*")
    }
  }
}