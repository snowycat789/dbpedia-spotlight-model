package org.dbpedia.spotlight.db.memory


import java.io._
import java.lang.Short

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.serializers.DefaultSerializers.KryoSerializableSerializer
import com.esotericsoftware.kryo.serializers.{DefaultArraySerializers, JavaSerializer}
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap
import org.dbpedia.spotlight.db.FSADictionary
import org.dbpedia.spotlight.db.model.{ResourceStore, TokenTypeStore}
import org.dbpedia.spotlight.log.SpotlightLog
import org.dbpedia.spotlight.model.{DBpediaType, FreebaseType, OntologyType, SchemaOrgType}

import scala.Predef._
import scala.collection.mutable.HashMap


/**
 * Base class for all memory stores.
 *
 * @author Joachim Daiber
 */

@SerialVersionUID(1001001)
abstract class MemoryStore extends Serializable {


  //The QC store is provided via the constructor, hence it should not be serialized as part of any MemoryStore
  @transient
  var quantizedCountStore: MemoryQuantizedCountStore = null

  def qc(quantizedCount: scala.Short): Int = quantizedCountStore.getCount(quantizedCount)

  /**
   * Method called after the store has been deserialized.
   * Implementations may override this method in order to finish setting
   * up the store.
   */
  def loaded() {}

  def size: Int
}


/**
 * Utility object for loading memory stores.
 */
object MemoryStore {

  val kryos = HashMap[String, Kryo]()

  kryos.put(classOf[MemoryResourceStore].getSimpleName,
  {
    val kryo = new Kryo()
    kryo.setRegistrationRequired(true)

    kryo.register(classOf[Array[Int]], new DefaultArraySerializers.IntArraySerializer())
    kryo.register(classOf[Array[scala.Short]], new DefaultArraySerializers.ShortArraySerializer())
    kryo.register(classOf[Array[String]], new DefaultArraySerializers.StringArraySerializer())
    kryo.register(classOf[Array[Array[Short]]], new JavaSerializer())

    kryo.register(classOf[OntologyType])
    kryo.register(classOf[DBpediaType])
    kryo.register(classOf[FreebaseType])
    kryo.register(classOf[SchemaOrgType])

    kryo.register(classOf[MemoryResourceStore])
    kryo.register(classOf[MemoryOntologyTypeStore], new JavaSerializer())

    kryo
  }
  )

  kryos.put(classOf[MemorySurfaceFormStore].getSimpleName,
  {
    val kryo = new Kryo()
    kryo.setRegistrationRequired(true)

    kryo.register(classOf[Array[scala.Short]], new DefaultArraySerializers.ShortArraySerializer())
    kryo.register(classOf[Array[scala.Int]], new DefaultArraySerializers.IntArraySerializer())
    kryo.register(classOf[Array[String]], new DefaultArraySerializers.StringArraySerializer())
    kryo.register(classOf[MemorySurfaceFormStore])
    kryo.register(classOf[java.util.Map[String, java.lang.Short]], new JavaSerializer())
    kryo.register(classOf[java.util.HashMap[String, Array[scala.Int]]], new JavaSerializer())
    kryo.register(classOf[Object2ShortOpenHashMap[String]], new JavaSerializer())

    kryo
  }
  )

  kryos.put(classOf[MemoryContextStore].getSimpleName,
  {
    val kryo = new Kryo()
    kryo.setRegistrationRequired(true)

    kryo.register(classOf[MemoryContextStore], new KryoSerializableSerializer())

    kryo
  }
  )

  kryos.put(classOf[MemoryCandidateMapStore].getSimpleName,
  {
    val kryo = new Kryo()
    kryo.setRegistrationRequired(true)

    kryo.register(classOf[MemoryCandidateMapStore], new JavaSerializer())

    kryo
  }
  )

  kryos.put(classOf[MemoryVectorStore].getSimpleName,{
    val kryo = new Kryo()
    kryo.setRegistrationRequired(true)

    kryo.register(classOf[MemoryVectorStore], new KryoSerializableSerializer())

    kryo
  })


  kryos.put(classOf[MemoryTokenTypeStore].getSimpleName,
  {
    val kryo = new Kryo()
    kryo.setRegistrationRequired(true)

    kryo.register(classOf[Array[Int]],    new DefaultArraySerializers.IntArraySerializer())
    kryo.register(classOf[Array[String]], new DefaultArraySerializers.StringArraySerializer())
    kryo.register(classOf[MemoryTokenTypeStore])

    kryo
  }
  )

  kryos.put(classOf[FSADictionary].getSimpleName,
  {
    val kryo = new Kryo()
    kryo.setRegistrationRequired(false)

    //kryo.register(classOf[FSADictionary], new JavaSerializer())

    kryo
  }
  )

  kryos.put(classOf[MemoryQuantizedCountStore].getSimpleName,
  {
    val kryo = new Kryo()
    kryo.setRegistrationRequired(false)
    kryo
  }
  )

  def load[T](in: InputStream, simpleName: String, quantizedCountStore: Option[MemoryQuantizedCountStore] = None): T = {

    val kryo: Kryo = kryos.get(simpleName).get

    SpotlightLog.info(this.getClass, "Loading %s...".format(simpleName))
    val sStart = System.currentTimeMillis()
    val input = new Input(in)

    val s = kryo.readClassAndObject(input).asInstanceOf[T]

    quantizedCountStore.foreach(qcs => s.asInstanceOf[MemoryStore].quantizedCountStore = qcs)
    s.asInstanceOf[MemoryStore].loaded()

    input.close()
    SpotlightLog.info(this.getClass, "Done (%d ms)".format(System.currentTimeMillis() - sStart))
    s
  }

  def loadTokenTypeStore(in: InputStream): MemoryTokenTypeStore = {
    load[MemoryTokenTypeStore](in, classOf[MemoryTokenTypeStore].getSimpleName)
  }

  def loadSurfaceFormStore(in: InputStream, quantizedCountStore: MemoryQuantizedCountStore): MemorySurfaceFormStore = {
    load[MemorySurfaceFormStore](in, classOf[MemorySurfaceFormStore].getSimpleName, Some(quantizedCountStore))
  }

  def loadResourceStore(in: InputStream, quantizedCountStore: MemoryQuantizedCountStore): MemoryResourceStore = {
    load[MemoryResourceStore](in, classOf[MemoryResourceStore].getSimpleName, Some(quantizedCountStore))
  }

  def loadCandidateMapStore(in: InputStream, resourceStore: ResourceStore, quantizedCountStore: MemoryQuantizedCountStore): MemoryCandidateMapStore = {
    val s = load[MemoryCandidateMapStore](in, classOf[MemoryCandidateMapStore].getSimpleName, Some(quantizedCountStore))
    s.resourceStore = resourceStore
    s
  }

  def loadContextStore(in: InputStream, tokenStore: TokenTypeStore, quantizedCountStore: MemoryQuantizedCountStore): MemoryContextStore = {
    val s = load[MemoryContextStore](in, classOf[MemoryContextStore].getSimpleName, Some(quantizedCountStore))
    s.tokenStore = tokenStore
    s.calculateTotalTokenCounts()
    s
  }

  def loadVectorStore(in: InputStream): MemoryVectorStore = {
    load[MemoryVectorStore](in, classOf[MemoryVectorStore].getSimpleName)
  }

  def loadFSADictionary(in: InputStream): FSADictionary = {
    load[FSADictionary](in, classOf[FSADictionary].getSimpleName)
  }

  def loadQuantizedCountStore(in: InputStream): MemoryQuantizedCountStore = {
    load[MemoryQuantizedCountStore](in, classOf[MemoryQuantizedCountStore].getSimpleName)
  }

  def dump(store: MemoryStore, out: File) {
    val kryo = kryos.get(store.getClass.getSimpleName).get

    //The QC store may not be serialized as part of the store, it is serialized separately
    store.quantizedCountStore = null

    SpotlightLog.info(this.getClass, "Writing %s...".format(store.getClass.getSimpleName))
    val output = new Output(new FileOutputStream(out))
    kryo.writeClassAndObject(output, store)

    output.close()
    SpotlightLog.info(this.getClass, "Done.")
  }


}

