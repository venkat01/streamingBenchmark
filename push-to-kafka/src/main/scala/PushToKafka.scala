package benchmark.common.kafkaPush


import java.util.Properties

import benchmark.common.Utils
import com.google.gson.JsonParser
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.io.Source._

/**
 * Created by sachin on 2/18/16.
 */

object PushToKafka {
  def main(args: Array[String]) {
    val commonConfig = Utils.findAndReadConfigFile(args(0), true).asInstanceOf[java.util.Map[String, Any]];

    val serializer = commonConfig.get("kafka.serializer") match {
      case s: String => s
      case other => throw new ClassCastException(other + " not a String")
    }
    val requiredAcks = commonConfig.get("kafka.requiredAcks") match {
      case s: String => s
      case other => throw new ClassCastException(other + " not a String")
    }
    val topic = commonConfig.get("kafka.topic") match {
      case s: String => s
      case other => throw new ClassCastException(other + " not a String")
    }
    val inputDirectory = commonConfig.get("data.kafka.inputDirectory") match {
      case s: String => s
      case other => throw new ClassCastException(other + " not a String")
    }

    val kafkaBrokers = commonConfig.get("kafka.brokers").asInstanceOf[java.util.List[String]] match {
      case l: java.util.List[String] => l.asScala.toSeq
      case other => throw new ClassCastException(other + " not a List[String]")
    }
    val kafkaPort = commonConfig.get("kafka.port") match {
      case n: Number => n.longValue()
      case other => throw new ClassCastException(other + " not a Number")
    }
    val recordLimitPerThread = commonConfig.get("data.kafka.Loader.thread.recordLimit") match {
      case n: Number => n.longValue()
      case other => throw new ClassCastException(other + " not a Number")
    }
    val awaitTimeForThread = commonConfig.get("data.kafka.Loader.thread.awaitTime") match {
      case n: Number => n.longValue()
      case other => throw new ClassCastException(other + " not a Number")
    }
    val loaderThreads = commonConfig.get("data.kafka.Loader.thread") match {
      case n: Number => n.intValue()
      case other => throw new ClassCastException(other + " not a Number")
    }

    // Create direct kafka stream with brokers and topics

    //        val brokerListString: String = "localhost:9092,localhost:9093,localhost:9094";
    //        val serializer: String = "kafka.serializer.StringEncoder";
    //        val requiredAcks: String = "1";
    //        val topic: String = "test1";
    //        val inputDirectory="/tmp/data/event";

    val brokerListString = new StringBuilder();

    for (host <- kafkaBrokers) {
      if (!brokerListString.isEmpty) {
        brokerListString.append(",")
      }
      brokerListString.append(host).append(":").append(kafkaPort)
    }

    // println("all props="+" brokerListString.toString()"+ brokerListString.toString()+"serializer"+serializer+"requiredAcks"+requiredAcks)
    /** Producer properties **/
    var props: Properties = new Properties()
    props.put("metadata.broker.list", brokerListString.toString())
    props.put("auto.offset.reset", "smallest")
    props.put("serializer.class", serializer)
    props.put("request.required.acks", requiredAcks)

    val config: ProducerConfig = new ProducerConfig(props)
    val producer: Producer[String, String] = new Producer[String, String](config)

    def send(text: String) {
      val data: KeyedMessage[String, String] = new KeyedMessage[String, String](topic, text)
      // println(text)

      producer.send(data);
    }


    val tasks: Seq[Future[Long]] = for (i <- 1 to loaderThreads) yield future {
      println("Executing task " + i)
      read()
    }

    def read(): Long = {
      var count: Long = 0
      while (count >= recordLimitPerThread)
      fromFile(inputDirectory).getLines.foreach(line => {
        val text = new JsonParser().parse(line).getAsJsonObject().get("text")
        //println(text.getAsString)
        // Thread.sleep(1)
        send(text.getAsString)
        count += 1
      })
      count
    }

    val aggregated: Future[Seq[Long]] = Future.sequence(tasks)

    val squares: Seq[Long] = Await.result(aggregated,awaitTimeForThread.seconds)
    producer.close()
  }
}
