//
// Copyright (C) 2005-2011 Cleversafe, Inc. All rights reserved.
//
// Contact Information:
// Cleversafe, Inc.
// 222 South Riverside Plaza
// Suite 1700
// Chicago, IL 60606, USA
//
// licensing@cleversafe.com
//
// END-OF-HEADER
//
// -----------------------
// @author: rveitch
//
// Date: Mar 27, 2014
// ---------------------

package com.cleversafe.oom.guice;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import com.cleversafe.oom.api.Producer;
import com.cleversafe.oom.cli.json.API;
import com.cleversafe.oom.cli.json.ClientConfig;
import com.cleversafe.oom.cli.json.Concurrency;
import com.cleversafe.oom.cli.json.FileSize;
import com.cleversafe.oom.cli.json.JSONConfiguration;
import com.cleversafe.oom.cli.json.OperationConfig;
import com.cleversafe.oom.distribution.Distribution;
import com.cleversafe.oom.distribution.NormalDistribution;
import com.cleversafe.oom.distribution.UniformDistribution;
import com.cleversafe.oom.guice.annotation.DefaultContainer;
import com.cleversafe.oom.guice.annotation.DefaultEntity;
import com.cleversafe.oom.guice.annotation.DefaultHeaders;
import com.cleversafe.oom.guice.annotation.DefaultHost;
import com.cleversafe.oom.guice.annotation.DefaultId;
import com.cleversafe.oom.guice.annotation.DefaultObjectLocation;
import com.cleversafe.oom.guice.annotation.DefaultPort;
import com.cleversafe.oom.guice.annotation.DefaultQueryParams;
import com.cleversafe.oom.guice.annotation.DefaultScheme;
import com.cleversafe.oom.guice.annotation.DefaultUriRoot;
import com.cleversafe.oom.guice.annotation.DeleteContainer;
import com.cleversafe.oom.guice.annotation.DeleteHeaders;
import com.cleversafe.oom.guice.annotation.DeleteHost;
import com.cleversafe.oom.guice.annotation.DeletePort;
import com.cleversafe.oom.guice.annotation.DeleteQueryParams;
import com.cleversafe.oom.guice.annotation.DeleteScheme;
import com.cleversafe.oom.guice.annotation.DeleteWeight;
import com.cleversafe.oom.guice.annotation.ReadContainer;
import com.cleversafe.oom.guice.annotation.ReadHeaders;
import com.cleversafe.oom.guice.annotation.ReadHost;
import com.cleversafe.oom.guice.annotation.ReadPort;
import com.cleversafe.oom.guice.annotation.ReadQueryParams;
import com.cleversafe.oom.guice.annotation.ReadScheme;
import com.cleversafe.oom.guice.annotation.ReadWeight;
import com.cleversafe.oom.guice.annotation.WriteContainer;
import com.cleversafe.oom.guice.annotation.WriteHeaders;
import com.cleversafe.oom.guice.annotation.WriteHost;
import com.cleversafe.oom.guice.annotation.WritePort;
import com.cleversafe.oom.guice.annotation.WriteQueryParams;
import com.cleversafe.oom.guice.annotation.WriteScheme;
import com.cleversafe.oom.guice.annotation.WriteWeight;
import com.cleversafe.oom.http.Scheme;
import com.cleversafe.oom.http.auth.BasicAuth;
import com.cleversafe.oom.http.auth.HttpAuth;
import com.cleversafe.oom.operation.Entity;
import com.cleversafe.oom.operation.OperationType;
import com.cleversafe.oom.operation.RequestContext;
import com.cleversafe.oom.scheduling.RequestRateScheduler;
import com.cleversafe.oom.scheduling.Scheduler;
import com.cleversafe.oom.util.Entities;
import com.cleversafe.oom.util.Pair;
import com.cleversafe.oom.util.WeightedRandomChoice;
import com.cleversafe.oom.util.producer.Producers;
import com.google.common.base.CharMatcher;
import com.google.common.math.DoubleMath;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class JsonModule extends AbstractModule
{
   private final JSONConfiguration config;
   private final static double err = Math.pow(0.1, 6);

   public JsonModule(final JSONConfiguration config)
   {
      this.config = checkNotNull(config, "config must not be null");
   }

   @Override
   protected void configure()
   {}

   @Provides
   JSONConfiguration provideJSONConfiguration()
   {
      return this.config;
   }

   @Provides
   @Singleton
   @DefaultId
   Producer<Long> provideDefaultIdProducer()
   {
      return new Producer<Long>()
      {
         private final AtomicLong id = new AtomicLong();

         @Override
         public Long produce(final RequestContext context)
         {
            return this.id.getAndIncrement();
         }
      };
   }

   @Provides
   @Singleton
   @DefaultScheme
   Producer<Scheme> provideDefaultScheme()
   {
      return Producers.of(this.config.getScheme());
   }

   @Provides
   @Singleton
   @WriteScheme
   Producer<Scheme> provideWriteScheme(@DefaultScheme final Producer<Scheme> scheme)
   {
      return provideScheme(OperationType.WRITE, scheme);
   }

   @Provides
   @Singleton
   @ReadScheme
   Producer<Scheme> provideReadScheme(@DefaultScheme final Producer<Scheme> scheme)
   {
      return provideScheme(OperationType.READ, scheme);
   }

   @Provides
   @Singleton
   @DeleteScheme
   Producer<Scheme> provideDeleteScheme(@DefaultScheme final Producer<Scheme> scheme)
   {
      return provideScheme(OperationType.DELETE, scheme);
   }

   private Producer<Scheme> provideScheme(
         final OperationType operationType,
         final Producer<Scheme> defaultScheme)
   {
      final OperationConfig config = this.config.getOperationConfig().get(operationType);
      if (config != null && config.getScheme() != null)
         return Producers.of(config.getScheme());
      return defaultScheme;
   }

   @Provides
   @Singleton
   @DefaultHost
   Producer<String> provideDefaultHost()
   {
      return createHost(this.config.getHosts());
   }

   @Provides
   @Singleton
   @WriteHost
   Producer<String> provideWriteHost(@DefaultHost final Producer<String> host)
   {
      return provideHost(OperationType.WRITE, host);
   }

   @Provides
   @Singleton
   @ReadHost
   Producer<String> provideReadHost(@DefaultHost final Producer<String> host)
   {
      return provideHost(OperationType.READ, host);
   }

   @Provides
   @Singleton
   @DeleteHost
   Producer<String> provideDeleteHost(@DefaultHost final Producer<String> host)
   {
      return provideHost(OperationType.DELETE, host);
   }

   private Producer<String> provideHost(
         final OperationType operationType,
         final Producer<String> defaultHost)
   {
      final OperationConfig config = this.config.getOperationConfig().get(operationType);
      if (config != null && config.getHosts() != null)
         return createHost(config.getHosts());
      return defaultHost;
   }

   private Producer<String> createHost(final List<String> hosts)
   {
      final WeightedRandomChoice<String> wrc = new WeightedRandomChoice<String>();
      for (final String host : hosts)
      {
         wrc.addChoice(host);
      }
      return Producers.of(wrc);
   }

   @Provides
   @Singleton
   @DefaultPort
   Producer<Integer> provideDefaultPort()
   {
      if (this.config.getPort() != null)
         return Producers.of(this.config.getPort());
      return null;
   }

   @Provides
   @Singleton
   @WritePort
   Producer<Integer> provideWritePort(@DefaultPort final Producer<Integer> port)
   {
      return providePort(OperationType.WRITE, port);
   }

   @Provides
   @Singleton
   @ReadPort
   Producer<Integer> provideReadPort(@DefaultPort final Producer<Integer> port)
   {
      return providePort(OperationType.READ, port);
   }

   @Provides
   @Singleton
   @DeletePort
   Producer<Integer> provideDeletePort(@DefaultPort final Producer<Integer> port)
   {
      return providePort(OperationType.DELETE, port);
   }

   private Producer<Integer> providePort(
         final OperationType operationType,
         final Producer<Integer> defaultPort)
   {
      final OperationConfig config = this.config.getOperationConfig().get(operationType);
      if (config != null && config.getPort() != null)
         return Producers.of(config.getPort());
      return defaultPort;
   }

   @Provides
   API provideApi()
   {
      return this.config.getApi();
   }

   @Provides
   @Singleton
   @DefaultUriRoot
   Producer<String> provideDefaultUriRoot()
   {
      if (this.config.getUriRoot() != null)
      {
         final String root = CharMatcher.is('/').trimFrom(this.config.getUriRoot());
         if (root.length() > 0)
            return Producers.of(root);
      }
      else
      {
         return Producers.of(this.config.getApi().toString().toLowerCase());
      }
      return null;
   }

   @Provides
   @Singleton
   @DefaultContainer
   Producer<String> provideDefaultContainer()
   {
      return Producers.of(this.config.getContainer());
   }

   @Provides
   @Singleton
   @WriteContainer
   Producer<String> provideWriteContainer(@DefaultContainer final Producer<String> container)
   {
      return provideContainer(OperationType.WRITE, container);
   }

   @Provides
   @Singleton
   @ReadContainer
   Producer<String> provideReadContainer(@DefaultContainer final Producer<String> container)
   {
      return provideContainer(OperationType.READ, container);
   }

   @Provides
   @Singleton
   @DeleteContainer
   Producer<String> provideDeleteContainer(@DefaultContainer final Producer<String> container)
   {
      return provideContainer(OperationType.DELETE, container);
   }

   private Producer<String> provideContainer(
         final OperationType operationType,
         final Producer<String> defaultContainer)
   {
      final OperationConfig config = this.config.getOperationConfig().get(operationType);
      if (config != null && config.getContainer() != null)
         return Producers.of(config.getContainer());
      return defaultContainer;
   }

   @Provides
   @Singleton
   @DefaultQueryParams
   Producer<Map<String, String>> provideDefaultQueryParams()
   {
      final Map<String, String> queryParams = new HashMap<String, String>();
      return Producers.of(queryParams);
   }

   @Provides
   @Singleton
   @WriteQueryParams
   Producer<Map<String, String>> provideWriteQueryParams(
         @DefaultQueryParams final Producer<Map<String, String>> defaultQueryParams)
   {
      return defaultQueryParams;
   }

   @Provides
   @Singleton
   @ReadQueryParams
   Producer<Map<String, String>> provideReadQueryParams(
         @DefaultQueryParams final Producer<Map<String, String>> defaultQueryParams)
   {
      return defaultQueryParams;
   }

   @Provides
   @Singleton
   @DeleteQueryParams
   Producer<Map<String, String>> provideDeleteQueryParams(
         @DefaultQueryParams final Producer<Map<String, String>> defaultQueryParams)
   {
      return defaultQueryParams;
   }

   @Provides
   @Singleton
   HttpAuth providesDefaultAuth()
   {
      final String username = this.config.getUsername();
      final String password = this.config.getPassword();

      if (username != null && password != null)
         return new BasicAuth(Producers.of(username), Producers.of(password));
      else if (username == null && password == null)
         return null;
      throw new IllegalArgumentException("If username is not null password must also be not null");
   }

   @Provides
   @Singleton
   @DefaultHeaders
   List<Producer<Pair<String, String>>> provideDefaultHeaders()
   {
      return createHeaders(this.config.getHeaders());
   }

   @Provides
   @Singleton
   @WriteHeaders
   List<Producer<Pair<String, String>>> provideWriteHeaders()
   {
      return provideHeaders(OperationType.WRITE);
   }

   @Provides
   @Singleton
   @ReadHeaders
   List<Producer<Pair<String, String>>> provideReadHeaders()
   {
      return provideHeaders(OperationType.READ);
   }

   @Provides
   @Singleton
   @DeleteHeaders
   List<Producer<Pair<String, String>>> provideDeleteHeaders()
   {
      return provideHeaders(OperationType.DELETE);
   }

   private List<Producer<Pair<String, String>>> provideHeaders(final OperationType operationType)
   {
      final Map<String, String> headers = this.config.getHeaders();
      final OperationConfig config = this.config.getOperationConfig().get(operationType);
      if (config != null && config.getHeaders() != null)
         headers.putAll(config.getHeaders());
      return createHeaders(headers);
   }

   private List<Producer<Pair<String, String>>> createHeaders(final Map<String, String> headers)
   {
      final List<Producer<Pair<String, String>>> h =
            new ArrayList<Producer<Pair<String, String>>>();
      for (final Entry<String, String> e : headers.entrySet())
      {
         h.add(Producers.of(new Pair<String, String>(e.getKey(), e.getValue())));
      }
      return h;
   }

   @Provides
   @Singleton
   @DefaultEntity
   Producer<Entity> provideDefaultEntity()
   {
      final WeightedRandomChoice<Distribution> wrc = new WeightedRandomChoice<Distribution>();
      for (final FileSize f : this.config.getFilesizes())
      {
         wrc.addChoice(createSizeDistribution(f), f.getWeight());
      }

      final JSONConfiguration config = this.config;
      return new Producer<Entity>()
      {
         private final WeightedRandomChoice<Distribution> sizes = wrc;

         @Override
         public Entity produce(final RequestContext context)
         {
            return Entities.of(config.getSource(), (long) this.sizes.nextChoice().nextSample());
         }
      };
   }

   private static Distribution createSizeDistribution(final FileSize filesize)
   {
      // TODO standardize terminology; mean or average
      final double mean = filesize.getAverage() * filesize.getAverageUnit().toBytes(1);
      final double spread = filesize.getSpread() * filesize.getSpreadUnit().toBytes(1);
      switch (filesize.getDistribution())
      {
      // TODO determine how to expose these in json configuration in a way that makes sense
      // mean/average/min/max?
         case NORMAL :
            return new NormalDistribution(mean, spread);
         default :
            return new UniformDistribution(mean, spread);
      }
   }

   // TODO simplify this method if possible
   @Provides
   @Singleton
   @DefaultObjectLocation
   String provideObjectLocation() throws IOException
   {

      String path = this.config.getObjectLocation();
      if (path == null || path.length() == 0)
         path = ".";

      final File f = new File(path).getCanonicalFile();
      if (!f.exists())
      {
         final boolean created = f.mkdirs();
         if (!created)
            throw new RuntimeException(String.format(
                  "Failed to create object location directory [%s]", f.toString()));
      }
      else if (!f.isDirectory())
      {
         throw new RuntimeException(String.format("Object location is not a directory [%s]",
               f.toString()));
      }
      return f.toString();
   }

   @Provides
   @WriteWeight
   double provideWriteWeight()
   {
      final double write = this.config.getWrite();
      checkArgument(inRange(write), "write must be in range [0.0, 100.0] [%s]", write);
      final double read = this.config.getRead();
      final double delete = this.config.getDelete();
      if (allEqual(0.0, write, read, delete))
         return 100.0;
      return write;
   }

   @Provides
   @ReadWeight
   double provideReadWeight()
   {
      final double read = this.config.getRead();
      checkArgument(inRange(read), "read must be in range [0.0, 100.0] [%s]", read);
      return read;
   }

   @Provides
   @DeleteWeight
   double provideDeleteWeight()
   {
      final double delete = this.config.getDelete();
      checkArgument(inRange(delete), "delete must be in range [0.0, 100.0] [%s]", delete);
      return delete;
   }

   private boolean inRange(final double v)
   {
      return DoubleMath.fuzzyCompare(v, 0.0, err) >= 0
            && DoubleMath.fuzzyCompare(v, 100.0, err) <= 0;
   }

   private boolean allEqual(final double compare, final double... values)
   {
      final double err = Math.pow(0.1, 6);
      for (final double v : values)
      {
         if (!DoubleMath.fuzzyEquals(v, compare, err))
            return false;
      }
      return true;
   }

   @Provides
   @Singleton
   Scheduler provideScheduler()
   {
      final Concurrency concurrency = this.config.getConcurrency();
      final Distribution count = new UniformDistribution(concurrency.getCount(), 0.0);
      return new RequestRateScheduler(count, concurrency.getUnit());
   }

   @Provides
   ClientConfig provideClientConfig()
   {
      return this.config.getClient();
   }
}
