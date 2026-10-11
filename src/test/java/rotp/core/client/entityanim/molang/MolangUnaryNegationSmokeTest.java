package rotp.core.client.entityanim.molang;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonPrimitive;

import rotp.core.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import rotp.core.config.MolangValue;
import team.unnamed.mocha.MochaEngine;
import team.unnamed.mocha.parser.MolangParser;
import team.unnamed.mocha.parser.ParseException;
import team.unnamed.mocha.runtime.MochaFunction;
import team.unnamed.mocha.runtime.value.ArrayValue;
import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

/** Evaluated parser regression; no source-text assertions or client startup. */
public final class MolangUnaryNegationSmokeTest {
    private static final List<String> FAILURES = new ArrayList<>();
    private static int checks;

    private MolangUnaryNegationSmokeTest() {}

    public static void main(String[] args) throws Exception {
        MochaEngine<Object> engine = fixtureEngine();
        for (Sample sample : List.of(
                new Sample("-math.max(2,3)", -3),
                new Sample("-math.min(-2,-3)", 3),
                new Sample("- math . max (2,3) + 5", 2),
                new Sample("2 * -math.max(2,3)", -6),
                new Sample("8 / -math.max(2,4)", -2),
                new Sample("--math.max(2,3)", 3),
                new Sample("---math.max(2,3)", -3),
                new Sample("-math.max(-math.min(2,3),1)", -1),
                new Sample("true ? -math.max(2,3) : 4", -3),
                new Sample("false ? 4 : -math.max(2,3)", -3),
                new Sample("return -math.max(2,3) + 5", 2),
                new Sample("t.value = -math.max(2,3); return t.value", -3),
                new Sample("-fixture.factory()()", -7),
                new Sample("-fixture.methods.next()", -7),
                new Sample("-fixture.methods.factory()()", -7),
                new Sample("-fixture.methods.value", -7),
                new Sample("-fixture.values[0]()", -7),
                new Sample("-fixture.values[1]()()", -7),
                new Sample("-fixture.array()[0]()", -7),
                new Sample("-fixture.array()[1]()()", -7),
                new Sample("-(math.max)(2,3)", -3),
                new Sample("-(fixture.factory())()", -7),
                new Sample("(-math.max)(2,3)", 0),
                new Sample("(-fixture.factory())()", 0),
                new Sample("5 - math.max(2,3)", 2),
                new Sample("5 - -math.max(2,3)", 8),
                new Sample("-2 * math.max(2,3)", -6),
                new Sample("-(2+3) * 4", -20),
                new Sample("-(math.max(2,3)) + 5", 2),
                new Sample("fixture.quoted('-math.max(2,3)')", 1),
                new Sample("fixture.comments('// -math.max(2,3) /* */')", 1),
                new Sample("fixture.unicode('\u4e2d\u6587') - math.max(2,3)", -2),
                new Sample("fixture.unicode('\u4e2d\u6587') + -math.max(2,3)", -2),
                new Sample("", 0),
                new Sample(" \t\r\n ", 0))) {
            run("expression \"" + sample.source() + "\"", () -> verify(engine, sample));
        }
        run("unsupported syntax contracts", () -> verifyUnsupportedSyntax(engine));
        run("malformed expression contracts", MolangUnaryNegationSmokeTest::verifyMalformed);
        run("reader I/O contracts", MolangUnaryNegationSmokeTest::verifyReaderFailures);
        run("one invocation per evaluation", MolangUnaryNegationSmokeTest::verifyInvocationCount);
        run("live HG query", MolangUnaryNegationSmokeTest::verifyLiveQuery);
        run("shared prepared calls with thread-local queries", MolangUnaryNegationSmokeTest::verifyThreadIsolation);
        if (!FAILURES.isEmpty()) {
            throw new AssertionError("Molang unary negation failures (" + FAILURES.size() + "): " + FAILURES);
        }
        System.out.println("Molang unary negation passed: " + checks + " evaluated checks");
    }

    private static MochaEngine<Object> fixtureEngine() {
        MochaEngine<Object> engine = MochaEngineWithoutJavassist.createStandard();
        Function<Object> number = (context, arguments) -> Value.of(7);
        Function<Object> factory = (context, arguments) -> number;
        MutableObjectBinding object = new MutableObjectBinding();
        object.set("next", number);
        object.set("factory", factory);
        object.set("value", Value.of(7));
        MutableObjectBinding fixture = new MutableObjectBinding();
        fixture.set("factory", factory);
        fixture.set("object", (Function<Object>) (context, arguments) -> object);
        fixture.set("methods", object);
        fixture.set("values", ArrayValue.of(number, factory));
        fixture.set("array", (Function<Object>) (context, arguments) -> ArrayValue.of(number, factory));
        fixture.set("quoted", (Function<Object>) (context, arguments) ->
                Value.of("-math.max(2,3)".equals(arguments.next().eval().getAsString())));
        fixture.set("comments", (Function<Object>) (context, arguments) ->
                Value.of("// -math.max(2,3) /* */".equals(arguments.next().eval().getAsString())));
        fixture.set("unicode", (Function<Object>) (context, arguments) ->
                Value.of("\u4e2d\u6587".equals(arguments.next().eval().getAsString())));
        engine.scope().set("fixture", fixture);
        return engine;
    }

    private static void verify(MochaEngine<?> engine, Sample sample) throws IOException {
        String source = sample.source();
        near(engine.eval(source), sample.expected(), "eval(String): " + source);
        near(engine.eval(new StringReader(source)), sample.expected(), "eval(Reader): " + source);
        near(engine.prepareEval(source).evaluate(), sample.expected(), "prepareEval(String): " + source);
        near(engine.prepareEval(new StringReader(source)).evaluate(), sample.expected(), "prepareEval(Reader): " + source);
        near(engine.eval(engine.parse(source)), sample.expected(), "parsed evaluation: " + source);
        near(MolangValue.fromJson(new JsonPrimitive(source), engine).getAsDouble(), sample.expected(), "JSON: " + source);
    }

    private static void verifyUnsupportedSyntax(MochaEngine<?> engine) throws IOException {
        // Do not fix other missing grammar or reinterpret unsupported comments in this arithmetic repair.
        for (String source : List.of("-math.max(2,3) // -math.max(4,5)",
                "-math.max(2,3) /* -math.max(4,5) */",
                "-fixture.object().next()", "-fixture.object().factory()()",
                "-(fixture.object).next()", "-fixture.values[0].value")) {
            List<team.unnamed.mocha.parser.ast.Expression> original;
            try {
                original = MolangParser.parseAll(source);
            }
            catch (ParseException failure) {
                try {
                    engine.parse(source);
                    throw new AssertionError("unsupported syntax stopped failing: " + source);
                }
                catch (ParseException actual) {
                    check(failure.getMessage().equals(actual.getMessage()), "unsupported syntax error changed: " + source);
                }
                continue;
            }
            var actual = engine.parse(source);
            check(original.toString().equals(actual.toString()), "unsupported syntax was rewritten: " + source);
            near(engine.eval(actual), engine.eval(original), "unsupported syntax result: " + source);
        }
    }

    private static void verifyMalformed() throws IOException {
        for (String source : List.of("-math.max(2,3", "-math.max(2,3]", "math.")) {
            AtomicInteger handled = new AtomicInteger();
            MochaEngine<?> engine = MochaEngineWithoutJavassist.createStandard()
                    .handleParseExceptions(error -> handled.incrementAndGet());
            near(engine.eval(source), 0, "malformed eval(String)");
            near(engine.eval(new StringReader(source)), 0, "malformed eval(Reader)");
            near(engine.prepareEval(source).evaluate(), 0, "malformed prepareEval(String)");
            near(engine.prepareEval(new StringReader(source)).evaluate(), 0, "malformed prepareEval(Reader)");
            near(MolangValue.fromJson(new JsonPrimitive(source), engine).getAsDouble(), 0, "malformed JSON");
            check(engine.prepareEval(source).toString().contains(source), "failure function lost original source");
            check(handled.get() == 6, "malformed expression callback count: " + source + " = " + handled.get());
            try {
                engine.parse(source);
                throw new AssertionError("parse accepted malformed source: " + source);
            }
            catch (ParseException expected) {
                checks++;
            }
        }
    }

    private static void verifyReaderFailures() {
        MochaEngine<?> engine = MochaEngineWithoutJavassist.createStandard();
        for (boolean prepared : new boolean[] { false, true }) {
            Reader reader = new Reader() {
                @Override public int read(char[] target, int offset, int length) throws IOException {
                    throw new IOException("fixture reader failure");
                }
                @Override public void close() {}
            };
            try {
                if (prepared) engine.prepareEval(reader); else engine.eval(reader);
                throw new AssertionError("reader failure was swallowed");
            }
            catch (UncheckedIOException expected) {
                check("fixture reader failure".equals(expected.getCause().getMessage()), "reader failure cause changed");
            }
        }
    }

    private static void verifyInvocationCount() {
        AtomicInteger calls = new AtomicInteger();
        MochaEngine<?> engine = MochaEngineWithoutJavassist.createStandard();
        engine.scope().set("counted", (Function<Object>) (context, arguments) -> {
            calls.incrementAndGet();
            return Value.of(3);
        });
        MochaFunction function = engine.prepareEval("-counted() + 5");
        check(calls.get() == 0, "normalization evaluated a call at preparation time");
        near(function.evaluate(), 2, "first invocation");
        near(function.evaluate(), 2, "second invocation");
        check(calls.get() == 2, "a call was duplicated or cached");
    }

    private static void verifyLiveQuery() {
        KeyframesMolangEngine.init();
        MochaEngine<?> engine = KeyframesMolangEngine.get();
        String source = "-math.max(-60, math.min(60, query.head_x_rotation)) / 4";
        MochaFunction string = engine.prepareEval(source);
        MochaFunction reader = engine.prepareEval(new StringReader(source));
        MolangValue json = MolangValue.fromJson(new JsonPrimitive(source), engine);
        try {
            for (float pitch : new float[] { -90, -60, -30, 0, 30, 60, 90, -15.5F }) {
                AnimMolangQuery.instance.fillContext(AnimMolangVariables.set(pitch, 0, 0));
                double expected = -Math.max(-60, Math.min(60, pitch)) / 4.0;
                near(string.evaluate(), expected, "live string pitch " + pitch);
                near(reader.evaluate(), expected, "live reader pitch " + pitch);
                near(json.getAsDouble(), expected, "live JSON pitch " + pitch);
                near(engine.eval("-query.head_x_rotation"), -pitch, "non-call member negation " + pitch);
            }
        }
        finally {
            AnimMolangQuery.instance.reset();
        }
    }

    private static void verifyThreadIsolation() throws InterruptedException {
        KeyframesMolangEngine.init();
        MochaFunction string = KeyframesMolangEngine.get().prepareEval("-math.max(0, query.anim_time)");
        MochaFunction reader = KeyframesMolangEngine.get().prepareEval(new StringReader("-math.max(0, query.anim_time)"));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        Thread[] threads = new Thread[2];
        for (int i = 0; i < threads.length; i++) {
            final float time = i == 0 ? 2 : 9;
            threads[i] = new Thread(() -> {
                try {
                    for (int frame = 0; frame < 250; frame++) {
                        AnimMolangQuery.instance.reset(time);
                        barrier.await(10, TimeUnit.SECONDS);
                        if (string.evaluate() != -time || reader.evaluate() != -time) {
                            throw new AssertionError("prepared negation lost thread-local query " + time);
                        }
                        barrier.await(10, TimeUnit.SECONDS);
                    }
                }
                catch (Throwable error) {
                    failures.add(error);
                    barrier.reset();
                }
                finally {
                    AnimMolangQuery.instance.reset();
                }
            }, "molang-negation-" + i);
            threads[i].setDaemon(true);
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join(20000);
            if (thread.isAlive()) {
                thread.interrupt();
                throw new AssertionError("negation query worker did not finish");
            }
        }
        check(failures.isEmpty(), "negation query workers failed: " + failures);
        checks += 1000;
    }

    private static void near(double actual, double expected, String label) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) < 1.0E-8,
                label + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void run(String label, CheckedRunnable test) {
        try {
            test.run();
            System.out.println("PASS " + label);
        }
        catch (Exception | AssertionError failure) {
            FAILURES.add(label + ": " + failure);
            System.out.println("FAIL " + label + ": " + failure);
        }
    }

    private record Sample(String source, double expected) {}
    private interface CheckedRunnable { void run() throws Exception; }
}
