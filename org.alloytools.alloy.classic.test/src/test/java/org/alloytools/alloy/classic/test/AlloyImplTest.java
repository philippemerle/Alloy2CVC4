package org.alloytools.alloy.classic.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.alloytools.alloy.classic.provider.AlloyClassicFacade;
import org.alloytools.alloy.core.api.Alloy;
import org.alloytools.alloy.core.api.AlloyModule;
import org.alloytools.alloy.core.api.TField;
import org.alloytools.alloy.core.api.TRun;
import org.alloytools.alloy.core.api.TSig;
import org.alloytools.alloy.solver.api.AlloyInstance;
import org.alloytools.alloy.solver.api.AlloySolution;
import org.alloytools.alloy.solver.api.AlloySolver;
import org.alloytools.alloy.solver.api.IAtom;
import org.junit.Test;

public class AlloyImplTest {
	Alloy ai = new AlloyClassicFacade();

	@Test
	public void testSolvers() {
		System.out.println(ai.getSolvers());
		assertTrue(ai.getSolvers()
			.size() > 0);

		assertNotNull(ai.getSolver("sat4j"));
		assertNotNull(ai.getSolver("minisat(jni)"));
	}

	@Test
	public void testSolversAll() {
		AlloyModule module = ai.compiler()
			.compileSource("run { 1 = 1 }");

		for (TRun run : module.getRuns()) {
			for (AlloySolver solver : ai.getSolvers()) {
				AlloySolution solution = solver.run(module, null, run);
				assertTrue(solution.iterator()
					.hasNext());
			}
		}
	}

	@Test
	public void iteratorImmutable() throws Exception {
		Alloy ai = new AlloyClassicFacade();
		AlloyModule module = ai.compiler()
			.compileSource("some sig B {}\n run show{} for 3");
		AlloySolver solver = ai.getSolvers()
			.get(0);
		TRun run = module.getRuns()
			.get(0);
		AlloySolution solution = solver.run(module, null, run);
		TSig B = module.getSig("B")
			.get();
			
		List<List<IAtom>> older = atoms(solution, B);
		List<List<IAtom>> newer = atoms(solution, B);
		
		assertEquals(3, newer.size());
		assertEquals(3, older.size());

		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				List<IAtom> olderList = older.get(i);
				List<IAtom> newerList = newer.get(i);
				if (i == j) {
					assertEquals(olderList, newerList);
				} else {
					assertFalse(!olderList.equals(newerList));
				}
			}
		}
	}

	private List<List<IAtom>> atoms(AlloySolution solution, TSig B) {
		return slurp(solution).stream()
			.map(inst -> inst.getAtoms(B)
				.asList())
			.collect(Collectors.toList());
	}

	private <T> List<T> slurp(Iterable<T> solution) {
		List<T> list = new ArrayList<>();
		solution.forEach(list::add);
		return list;
	}

	@Test
	public void iterator() throws Exception {
		AlloyModule module = ai.compiler()
			.compileSource("pred two[y:Int] { y = 1 or y = 2 or y = 3 } run two ");

		AlloySolver solver = ai.getSolvers()
			.get(0);
		for (TRun run : module.getRuns()) {

			AlloySolution solution = solver.run(module, null, run);
			List<Integer> collect = solution.stream()
				.map(instance -> instance.getVariable("two", "y")
					.scalar()
					.toInt())
				.sorted()
				.collect(Collectors.toList());
			assertEquals(Arrays.asList(1, 2, 3), collect);
		}
	}

	@Test(expected = IllegalStateException.class)
	public void testIteratorNoElements() throws Exception {
		AlloyModule module = ai.compiler()
			.compileSource("pred nothing[y:Int] { y = 1 and y = 2 } run nothing ");

		AlloySolver solver = ai.getSolvers()
			.get(0);

		TRun run = module.getRuns()
			.get(0);

		AlloySolution solution = solver.run(module, null, run);
		assertFalse(solution.isSatisfied());
		solution.iterator()
			.next();
	}

	@Test
	public void simple() throws Exception {
		AlloyModule module = ai.compiler()
			.compileSource("some sig B {}\n" + "some sig A {  x : \"abc\" } \n" + "run Foo2 { #A =1 } for 2");
		assertNotNull(module);
		System.out.println("Sigs " + module.getSigs());
		System.out.println("Runs " + module.getRuns());

		AlloySolver solver = ai.getSolvers()
			.get(0);
		for (TRun run : module.getRuns()) {

			AlloySolution solution = solver.run(module, null, run);

			for (AlloyInstance instance : solution) {

				for (TSig sig : module.getSigs()) {
					System.out.println(sig + "\t" + instance.getAtoms(sig));
					for (TField field : sig.getFields()) {
						System.out.println("\t" + field.getName() + " " + instance.getField(field));
					}
				}
			}
		}
	}

	@Test
	public void expects() throws Exception {
		Alloy ai = new AlloyClassicFacade();
		AlloyModule module = ai.compiler()
			.compileSource(//
				"pred foo[x, y, z: Int] {" //
					+ " x < 5 and y < 5\n" //
					+ " x.add[y] = z and x > y and z < x" //
					+ "} "//
					+ "run foo for 5 int");

		AlloySolver solver = ai.getSolvers()
			.get(0);

		for (TRun run : module.getRuns()) {

			AlloySolution solution = solver.run(module, null, run);

			for (AlloyInstance instance : solution) {
				int x = instance.getVariable(run.getName(), "x")
					.scalar()
					.toInt();
				int y = instance.getVariable(run.getName(), "y")
					.scalar()
					.toInt();
				int z = instance.getVariable(run.getName(), "z")
					.scalar()
					.toInt();
				System.out.println("x+y=z : " + x + "+" + y + "=" + z);
				assertEquals(z, x + y);
			}
		}
	}

	@Test
	public void sortedOutput() throws Exception {
		Alloy ai = new AlloyClassicFacade();
		AlloyModule module = ai.compiler()
			.compileSource("some sig B {}\nrun { #B =9 } for 16");

		AlloySolver solver = ai.getSolvers()
			.get(0);
		for (TRun run : module.getRuns()) {

			AlloySolution solution = solver.run(module, null, run);
			// TSig B = module.getSig("B").get();

			for (AlloyInstance instance : solution) {
				System.out.println(solution.none());
				System.out.println(instance.universe());
				System.out.println(instance.ident());
			}
		}
	}

	@Test
	public void commands() throws Exception {
		Alloy ai = new AlloyClassicFacade();
		AlloyModule module = ai.compiler()
			.compileSource("some sig B {}");

		AlloySolver solver = ai.getSolvers()
			.get(0);

		for (TRun run : module.getRuns()) {

			AlloySolution solution = solver.run(module, null, run);

			TSig B = module.getSig("B")
				.get();
			TSig univ = module.getSig("univ")
				.get();

			for (AlloyInstance instance : solution) {
				System.out.println(instance.getAtoms(univ));
				System.out.println(instance.getAtoms(B));
			}
		}
	}
}