/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark && the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientsearch.web.controller;

import java.io.Writer;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.io.PrintWriter;
import javax.servlet.http.HttpSession;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import java.lang.StackTraceElement;
import org.openmrs.User;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.format.annotation.DateTimeFormat;
import org.openmrs.module.patientsearch.PatientSearchService;

@Controller("$patientsearch.MFPatientSearchController")
@RequestMapping(value = "module/patientsearch", method = RequestMethod.GET, produces = "application/json")
public class MFPatientSearchController {
	
	/** Logger for this class && subclasses */
	protected final Log log = LogFactory.getLog(getClass());
	
	@Autowired
	UserService userService;
	
	private DateFormat df = new SimpleDateFormat("ddMMyyyy");
	
	/**
	 * Initially called after the getUsers method to get the landing form name
	 * 
	 * @return String form view name
	 */
	@RequestMapping(value = "search.form")
	public void onGet(Writer responseWriter, @RequestParam(value = "name", required = false) String name,
	        @RequestParam(value = "name_type", required = false) String name_type,
	        @RequestParam(value = "age_from", required = false) String age_from,
	        @RequestParam(value = "age_to", required = false) String age_to,
	        @RequestParam(value = "gender", required = false) String gender,
	        @RequestParam(value = "id_type_uuid", required = false) String id_type_uuid,
	        @RequestParam(value = "id", required = false) String id,
	        @RequestParam(value = "address", required = false) String address,
	        @RequestParam(value = "visit_date_from", required = false) String visit_date_from,
	        @RequestParam(value = "visit_date_to", required = false) String visit_date_to,
	        @RequestParam(value = "provider_uuid", required = false) String provider_uuid,
	        @RequestParam(value = "diag_or_obs_uuid", required = false) String diag_or_obs_uuid) {
		if (Context.isAuthenticated()) {
			try {
				PatientSearchService patientSearch = new PatientSearchService();
				if (name != null)
					patientSearch.addCriteriaName(name, name_type);
				if (age_from != null && age_to != null) {
					int a1 = Integer.parseInt(age_from);
					int a2 = Integer.parseInt(age_to);
					patientSearch.addCriteriaAge(a1, a2);
				}
				if (gender != null)
					patientSearch.addCriteriaGender(gender);
				if (id != null)
					patientSearch.addCriteriaID(id_type_uuid, id);
				if (address != null)
					patientSearch.addCriteriaAddress(address);
				if (visit_date_from != null && visit_date_to != null) {
					Date date_from = df.parse(visit_date_from);
					Date date_to = df.parse(visit_date_to);
					patientSearch.addCriteriaVisitDate(date_from, date_to);
				}
				if (provider_uuid != null)
					patientSearch.addCriteriaProvider(provider_uuid);
				if (diag_or_obs_uuid != null)
					patientSearch.addCriteriaDiagOrObs(diag_or_obs_uuid);
				JSONObject result = patientSearch.search();
				result.write(responseWriter);
			}
			catch (Exception e) {
				try {
					JSONObject json_error = new JSONObject();
					json_error.put("error", String.format("Failed to perform search: %s", e.getMessage()));
					json_error.put("stack_trace", e.getStackTrace().toString());
					json_error.write(responseWriter);
				}
				catch (Exception e2) {}
			}
		} else {
			try {
				JSONObject json_error = new JSONObject();
				json_error.put("error", String.format("User not authenticated"));
				json_error.write(responseWriter);
			}
			catch (Exception e2) {}
		}
	}
}
