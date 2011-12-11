package akka.dispatch;

import akka.actor.Timeout;
import akka.actor.ActorSystem;

import akka.util.Duration;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.Callable;
import java.util.LinkedList;
import java.lang.Iterable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import akka.japi.Function;
import akka.japi.Function2;
import akka.japi.Procedure;
import akka.japi.Option;
import akka.testkit.AkkaSpec;

public class JavaFutureTests {

  private static ActorSystem system;
  private volatile static FutureFactory ff;
  private static Timeout t;

  @BeforeClass
  public static void beforeAll() {
    system = ActorSystem.create("JavaFutureTests", AkkaSpec.testConf());
    t = system.settings().ActorTimeout();
    ff = new FutureFactory(system.dispatcher());
  }

  @AfterClass
  public static void afterAll() {
    system.stop();
    system = null;
  }

  @Test
  public void mustBeAbleToMapAFuture() {

    Future<String> f1 = ff.future(new Callable<String>() {
      public String call() {
        return "Hello";
      }
    });

    Future<String> f2 = f1.map(new Function<String, String>() {
      public String apply(String s) {
        return s + " World";
      }
    });

    assertEquals("Hello World", f2.get());
  }

  @Test
  public void mustBeAbleToExecuteAnOnResultCallback() throws Throwable {
    final CountDownLatch latch = new CountDownLatch(1);
    Promise<String> cf = ff.<String>promise();
    Future<String> f = cf;
    f.onResult(new Procedure<String>() {
      public void apply(String result) {
        if (result.equals("foo"))
          latch.countDown();
      }
    });

    cf.completeWithResult("foo");
    assertTrue(latch.await(5000, TimeUnit.MILLISECONDS));
    assertEquals(f.get(), "foo");
  }

  @Test
  public void mustBeAbleToExecuteAnOnExceptionCallback() throws Throwable {
    final CountDownLatch latch = new CountDownLatch(1);
    Promise<String> cf = ff.<String>promise();
    Future<String> f = cf;
    f.onException(new Procedure<Throwable>() {
      public void apply(Throwable t) {
        if (t instanceof NullPointerException)
          latch.countDown();
      }
    });

    Throwable exception = new NullPointerException();
    cf.completeWithException(exception);
    assertTrue(latch.await(5000, TimeUnit.MILLISECONDS));
    assertEquals(f.exception().get(), exception);
  }

  @Test
  public void mustBeAbleToExecuteAnOnCompleteCallback() throws Throwable {
    final CountDownLatch latch = new CountDownLatch(1);
    Promise<String> cf = ff.<String>promise();
    Future<String> f = cf;
    f.onComplete(new Procedure<Future<String>>() {
      public void apply(akka.dispatch.Future<String> future) {
        latch.countDown();
      }
    });

    cf.completeWithResult("foo");
    assertTrue(latch.await(5000, TimeUnit.MILLISECONDS));
    assertEquals(f.get(), "foo");
  }

  @Test
  public void mustBeAbleToForeachAFuture() throws Throwable {
    final CountDownLatch latch = new CountDownLatch(1);
    Promise<String> cf = ff.<String>promise();
    Future<String> f = cf;
    f.foreach(new Procedure<String>() {
      public void apply(String future) {
        latch.countDown();
      }
    });

    cf.completeWithResult("foo");
    assertTrue(latch.await(5000, TimeUnit.MILLISECONDS));
    assertEquals(f.get(), "foo");
  }

  @Test
  public void mustBeAbleToFlatMapAFuture() throws Throwable {
    final CountDownLatch latch = new CountDownLatch(1);
    Promise<String> cf = ff.<String>promise();
    cf.completeWithResult("1000");
    Future<String> f = cf;
    Future<Integer> r = f.flatMap(new Function<String, Future<Integer>>() {
      public Future<Integer> apply(String r) {
        latch.countDown();
        Promise<Integer> cf = ff.<Integer>promise();
        cf.completeWithResult(Integer.parseInt(r));
        return cf;
      }
    });

    assertEquals(f.get(), "1000");
    assertEquals(r.get().intValue(), 1000);
    assertTrue(latch.await(5000, TimeUnit.MILLISECONDS));
  }

  @Test
  public void mustBeAbleToFilterAFuture() throws Throwable {
    final CountDownLatch latch = new CountDownLatch(1);
    Promise<String> cf = ff.<String>promise();
    Future<String> f = cf;
    Future<String> r = f.filter(new Function<String, Boolean>() {
      public Boolean apply(String r) {
        latch.countDown();
        return r.equals("foo");
      }
    });

    cf.completeWithResult("foo");
    assertTrue(latch.await(5000, TimeUnit.MILLISECONDS));
    assertEquals(f.get(), "foo");
    assertEquals(r.get(), "foo");
  }

  // TODO: Improve this test, perhaps with an Actor
  @Test
  public void mustSequenceAFutureList() {
    LinkedList<Future<String>> listFutures = new LinkedList<Future<String>>();
    LinkedList<String> listExpected = new LinkedList<String>();

    for (int i = 0; i < 10; i++) {
      listExpected.add("test");
      listFutures.add(ff.future(new Callable<String>() {
        public String call() {
          return "test";
        }
      }));
    }

    Future<Iterable<String>> futureList = ff.sequence(listFutures);

    assertEquals(futureList.get(), listExpected);
  }

  // TODO: Improve this test, perhaps with an Actor
  @Test
  public void foldForJavaApiMustWork() {
    LinkedList<Future<String>> listFutures = new LinkedList<Future<String>>();
    StringBuilder expected = new StringBuilder();

    for (int i = 0; i < 10; i++) {
      expected.append("test");
      listFutures.add(ff.future(new Callable<String>() {
        public String call() {
          return "test";
        }
      }));
    }

    Future<String> result = ff.fold("", listFutures, new Function2<String, String, String>() {
      public String apply(String r, String t) {
        return r + t;
      }
    });

    assertEquals(result.get(), expected.toString());
  }

  @Test
  public void reduceForJavaApiMustWork() {
    LinkedList<Future<String>> listFutures = new LinkedList<Future<String>>();
    StringBuilder expected = new StringBuilder();

    for (int i = 0; i < 10; i++) {
      expected.append("test");
      listFutures.add(ff.future(new Callable<String>() {
        public String call() {
          return "test";
        }
      }));
    }

    Future<String> result = ff.reduce(listFutures, new Function2<String, String, String>() {
      public String apply(String r, String t) {
        return r + t;
      }
    });

    assertEquals(result.get(), expected.toString());
  }

  @Test
  public void traverseForJavaApiMustWork() {
    LinkedList<String> listStrings = new LinkedList<String>();
    LinkedList<String> expectedStrings = new LinkedList<String>();

    for (int i = 0; i < 10; i++) {
      expectedStrings.add("TEST");
      listStrings.add("test");
    }

    Future<Iterable<String>> result = ff.traverse(listStrings, new Function<String, Future<String>>() {
      public Future<String> apply(final String r) {
        return ff.future(new Callable<String>() {
          public String call() {
            return r.toUpperCase();
          }
        });
      }
    });

    assertEquals(result.get(), expectedStrings);
  }

  @Test
  public void findForJavaApiMustWork() {
    LinkedList<Future<Integer>> listFutures = new LinkedList<Future<Integer>>();
    for (int i = 0; i < 10; i++) {
      final Integer fi = i;
      listFutures.add(ff.future(new Callable<Integer>() {
        public Integer call() {
          return fi;
        }
      }));
    }
    final Integer expect = 5;
    Future<Option<Integer>> f = ff.find(listFutures, new Function<Integer, Boolean>() {
      public Boolean apply(Integer i) {
        return i == 5;
      }
    });

    assertEquals(expect, Block.sync(f, Duration.create(5, TimeUnit.SECONDS)));
  }

  @Test
  public void BlockMustBeCallable() {
    Promise<String> p = ff.<String>promise();
    Duration d = Duration.create(1, TimeUnit.SECONDS);
    p.completeWithResult("foo");
    Block.on(p, d);
    assertEquals(Block.sync(p, d), "foo");
  }
}
