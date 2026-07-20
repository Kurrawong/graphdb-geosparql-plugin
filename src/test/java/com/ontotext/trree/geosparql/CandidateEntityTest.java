package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CandidateEntityTest {
	@Test
	public void candidateEntityOwnsAnImmutableDefensiveCopy() {
		IndexGeometry geometry = TestIndexGeometries.exactlyOne(SourceGeometryLiteral.fromWkt("POINT(0 0)"));
		List<IndexGeometry> input = new ArrayList<>();
		input.add(geometry);

		CandidateEntity candidate = new CandidateEntity(7, input);
		input.clear();

		assertEquals(7, candidate.entityId());
		assertEquals(List.of(geometry), candidate.matchingGeometries());
		assertThrows(UnsupportedOperationException.class,
				() -> candidate.matchingGeometries().add(geometry));
	}

	@Test
	public void candidateEntityRejectsInvalidState() {
		IndexGeometry geometry = TestIndexGeometries.exactlyOne("POINT(0 0)");

		assertThrows(IllegalArgumentException.class, () -> new CandidateEntity(0, List.of(geometry)));
		assertThrows(NullPointerException.class, () -> new CandidateEntity(1, null));
		assertThrows(IllegalArgumentException.class, () -> new CandidateEntity(1, List.of()));
		assertThrows(NullPointerException.class, () -> new CandidateEntity(1, java.util.Arrays.asList(geometry, null)));
	}
}
