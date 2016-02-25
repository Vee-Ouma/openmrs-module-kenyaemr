/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.kenyaemr.calculation.library.mchcs;

import org.joda.time.DateTime;
import org.joda.time.Months;
import org.joda.time.Weeks;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Person;
import org.openmrs.Program;
import org.openmrs.api.context.Context;
import org.openmrs.calculation.patient.PatientCalculationContext;
import org.openmrs.calculation.result.CalculationResultMap;
import org.openmrs.module.kenyacore.calculation.AbstractPatientCalculation;
import org.openmrs.module.kenyacore.calculation.BooleanResult;
import org.openmrs.module.kenyacore.calculation.Calculations;
import org.openmrs.module.kenyacore.calculation.Filters;
import org.openmrs.module.kenyacore.calculation.PatientFlagCalculation;
import org.openmrs.module.metadatadeploy.MetadataUtils;
import org.openmrs.module.kenyaemr.Dictionary;
import org.openmrs.module.kenyaemr.calculation.EmrCalculationUtils;
import org.openmrs.module.kenyaemr.metadata.MchMetadata;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * Determines whether a child at 6 week and above has had PCR test
 */
public class NeedsPcrTestCalculation extends AbstractPatientCalculation implements PatientFlagCalculation {

	/**
	 * @see org.openmrs.module.kenyacore.calculation.PatientFlagCalculation#getFlagMessage()
	 */
	@Override
	public String getFlagMessage() {
		return "Due For PCR Test";
	}

	/**
	 * @see org.openmrs.calculation.patient.PatientCalculation#evaluate(java.util.Collection, java.util.Map, org.openmrs.calculation.patient.PatientCalculationContext)
	 */
	@Override
	public CalculationResultMap evaluate(Collection<Integer> cohort, Map<String, Object> parameterValues, PatientCalculationContext context) {

		Program mchcsProgram = MetadataUtils.existing(Program.class, MchMetadata._Program.MCHCS);

		// Get all patients who are alive and in MCH-CS program
		Set<Integer> alive = Filters.alive(cohort, context);
		Set<Integer> inMchcsProgram = Filters.inProgram(mchcsProgram, alive, context);

		// Get whether the child is HIV Exposed
		CalculationResultMap lastChildHivStatus = Calculations.lastObs(Dictionary.getConcept(Dictionary.CHILDS_CURRENT_HIV_STATUS), cohort, context);
		CalculationResultMap lastPcrTestReaction = Calculations.lastObs(Dictionary.getConcept(Dictionary.HIV_DNA_POLYMERASE_CHAIN_REACTION), cohort, context);
		CalculationResultMap lastPcrTestQualitative = Calculations.lastObs(Dictionary.getConcept(Dictionary.HIV_DNA_POLYMERASE_CHAIN_REACTION_QUALITATIVE), cohort, context);

		Concept hivExposed = Dictionary.getConcept(Dictionary.EXPOSURE_TO_HIV);

		CalculationResultMap ret = new CalculationResultMap();

		for (Integer ptId : cohort) {

			boolean needsPcr = false;

			// Check if a patient is alive and is in MCHCS program
			if (inMchcsProgram.contains(ptId)) {

				Obs hivStatusObs = EmrCalculationUtils.obsResultForPatient(lastChildHivStatus, ptId);
				Obs pcrTestObs =  EmrCalculationUtils.obsResultForPatient(lastPcrTestReaction, ptId);
				Obs pcrTestObsQual =  EmrCalculationUtils.obsResultForPatient(lastPcrTestReaction, ptId);

				//get birth date of this patient
				Person person = Context.getPersonService().getPerson(ptId);

				if (hivStatusObs != null && pcrTestObs == null &&  pcrTestObsQual == null && (hivStatusObs.getValueCoded().equals(hivExposed)) && getAgeInWeeks(person.getBirthdate(), context.getNow()) >= 6 && getAgeInMonts(person.getBirthdate(), context.getNow()) <= 9) {
					needsPcr = true;
				}

			}

			ret.put(ptId, new BooleanResult(needsPcr, this, context));
		}
		return ret;
	}

	Integer getAgeInWeeks(Date birtDate, Date context) {
		DateTime d1 = new DateTime(birtDate.getTime());
		DateTime d2 = new DateTime(context.getTime());
		return Weeks.weeksBetween(d1, d2).getWeeks();
	}

	Integer getAgeInMonts(Date birtDate, Date context) {
		DateTime d1 = new DateTime(birtDate.getTime());
		DateTime d2 = new DateTime(context.getTime());
		return Months.monthsBetween(d1, d2).getMonths();
	}
}