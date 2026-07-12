package com.ontotext.trree.geosparql.lucene;

import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class SourceGeometryLiteralResolverTest {

	@Test
	public void identicalPersistedSourceIdentityLoadsOnce() {
		AtomicInteger loadCount = new AtomicInteger();
		SourceGeometryLiteral source = SourceGeometryLiteral.fromWkt("POINT(1 2)");
		SourceGeometryLiteralResolver resolver = new SourceGeometryLiteralResolver(identity -> {
			loadCount.incrementAndGet();
			return source;
		});
		StoredSourceGeometryIdentity identity = new StoredSourceGeometryIdentity(
				"POINT(1 2)", source.datatype().stringValue(), source.effectiveCrsUri());

		assertSame(resolver.resolve(identity), resolver.resolve(identity));
		assertEquals(1, loadCount.get());
	}

	@Test
	public void everyPersistedSourceIdentityFieldParticipatesInCaching() {
		AtomicInteger loadCount = new AtomicInteger();
		SourceGeometryLiteral source = SourceGeometryLiteral.fromWkt("POINT(1 2)");
		SourceGeometryLiteralResolver resolver = new SourceGeometryLiteralResolver(identity -> {
			loadCount.incrementAndGet();
			return source;
		});

		resolver.resolve(new StoredSourceGeometryIdentity("POINT(1 2)", "datatype:one", "crs:one"));
		resolver.resolve(new StoredSourceGeometryIdentity("POINT(2 3)", "datatype:one", "crs:one"));
		resolver.resolve(new StoredSourceGeometryIdentity("POINT(1 2)", "datatype:two", "crs:one"));
		resolver.resolve(new StoredSourceGeometryIdentity("POINT(1 2)", "datatype:one", "crs:two"));

		assertEquals(4, loadCount.get());
	}

	@Test
	public void clearingAtEntityBoundaryLoadsIdentityAgain() {
		AtomicInteger loadCount = new AtomicInteger();
		SourceGeometryLiteralResolver resolver = new SourceGeometryLiteralResolver(identity -> {
			loadCount.incrementAndGet();
			return SourceGeometryLiteral.fromWkt(identity.lexicalForm());
		});
		StoredSourceGeometryIdentity identity = new StoredSourceGeometryIdentity(
				"POINT(1 2)", "datatype", "crs");

		resolver.resolve(identity);
		resolver.clear();
		resolver.resolve(identity);

		assertEquals(2, loadCount.get());
	}

	@Test
	public void failedLoadsAreNotCached() {
		AtomicInteger loadCount = new AtomicInteger();
		SourceGeometryLiteralResolver resolver = new SourceGeometryLiteralResolver(identity -> {
			loadCount.incrementAndGet();
			throw new IllegalArgumentException("invalid source metadata");
		});
		StoredSourceGeometryIdentity identity = new StoredSourceGeometryIdentity(
				"POINT(1 2)", "datatype", "crs");

		assertThrows(IllegalArgumentException.class, () -> resolver.resolve(identity));
		assertThrows(IllegalArgumentException.class, () -> resolver.resolve(identity));
		assertEquals(2, loadCount.get());
	}
}
