package sneps.snip;

import sneps.network.VariableNode;
import sneps.snip.matching.Binding;
import sneps.snip.matching.LinearSubstitutions;
import sneps.snip.matching.Substitutions;

public class Filter {
	private Substitutions substitutions;

	public Filter() {
		this.substitutions = new LinearSubstitutions();
	}

	public Filter(Substitutions substitution) {
		this.substitutions = substitution;
	}

	public Substitutions getSubstitutions() {
		return substitutions;
	}

	@Override
	public boolean equals(Object filter) {
		Filter typeCastedObject = (Filter) filter;
		if (typeCastedObject == null)
			return false;
		return this.substitutions.isEqual(typeCastedObject.getSubstitutions());
	}

	public boolean canPass(Report report) {
		for (int i = 0; i < substitutions.cardinality(); i++) {
			Binding currentFilterBinding = substitutions.getBinding(i);
			VariableNode currentFilterVariableNode = currentFilterBinding.getVariableNode();
			Binding currentReportBinding = report.getSubstitutions().getBindingByVariable(currentFilterVariableNode);
//			System.out.println("Bindings " + currentFilterBinding + " " + report.getSubstitutions());
			if (currentReportBinding != null && currentFilterBinding.getNode() != currentReportBinding.getNode())
				return false;
		}
		return true;
	}

}
