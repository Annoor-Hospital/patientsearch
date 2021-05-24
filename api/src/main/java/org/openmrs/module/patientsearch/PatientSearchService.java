/**
 * This Source Code Form is subject to the terms of the MIT License. If a copy
 * of the MPL was not distributed with this file, You can obtain one at 
 * https://opensource.org/licenses/MIT.
 */
package org.openmrs.module.patientsearch;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.lang.Throwable;
import java.lang.ClassLoader;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.io.FileUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.ResultSet;
import org.openmrs.util.DatabaseUpdater;

public class PatientSearchService {
	
	private DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
	
	private List<String> select_list = new ArrayList<String>();
	
	private List<String> join_list = new ArrayList<String>();
	
	private List<String> where_list = new ArrayList<String>();
	
	private String query;
	
	private int has_encounter_join = 0;
	
	public PatientSearchService() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		File sqlfile = new File(classLoader.getResource("query.sql").getFile());
		query = FileUtils.readFileToString(sqlfile);
	}
	
	private String scrub_name(String name) {
		name = name.replaceAll("[^a-zA-Z0-9\u0600-\u06FF -]", "");
		if (name.length() > 80)
			name = name.substring(0, 80);
		return name;
	}
	
	private String scrub_uuid(String uuid) {
		uuid = uuid.replaceAll("[^a-z0-9-]", "");
		if (uuid.length() > 80)
			uuid = uuid.substring(0, 80);
		return uuid;
	}
	
	public void addCriteriaName(String name, String name_type) throws Exception {
		name = scrub_name(name);
		if (name_type == null) {
			where_list
			        .add(String
			                .format(
			                    "(concat_ws(' ', pn.given_name, COALESCE(pn.middle_name,''), COALESCE(pn.family_name,'')) LIKE '%%%s%%' OR concat_ws(' ',COALESCE(pa1.value,''),COALESCE(pa2.value,''),COALESCE(pa3.value,'')) LIKE '%%%s%%')",
			                    name, name));
		} else {
			if (name_type.equals("firstname")) {
				where_list.add(String.format("(pn.given_name LIKE '%%%s%%' OR pa1.value LIKE '%%%s%%')", name, name));
			} else {
				throw new Exception("Name type not supported");
			}
		}
	}
	
	public void addCriteriaAge(int age_from, int age_to) throws Exception {
		if (age_from >= 0 && age_to <= 140) {
			where_list
			        .add(String
			                .format(
			                    "DATE_FORMAT(NOW(), '%%Y') - DATE_FORMAT(p.birthdate, '%%Y') - (DATE_FORMAT(NOW(), '00-%%m-%%d') < DATE_FORMAT(p.birthdate, '00-%%m-%%d')) between %d and %d",
			                    age_from, age_to));
		} else {
			throw new Exception("Age out of range");
		}
	}
	
	public void addCriteriaGender(String gender) throws Exception {
		if (gender.equals("male") || gender.equals("female")) {
			gender = gender.equals("male") ? "M" : "F";
			where_list.add(String.format("p.gender = '%s'", gender));
		} else {
			throw new Exception("gender not valid");
		}
	}
	
	// type of 0 seaches all identifiers
	public void addCriteriaID(String id_type_uuid, String id) {
		id = scrub_name(id);
		if (id_type_uuid != null)
			id_type_uuid = scrub_uuid(id_type_uuid);
		
		select_list.add("concat(pit.name, \": \", pi.identifier) as searched_identifier");
		
		join_list.add("join patient_identifier as pi on p.person_id = pi.patient_id and pi.voided is false");
		join_list.add("join patient_identifier_type as pit on pi.identifier_type = pit.patient_identifier_type_id");
		if (id_type_uuid != null) {
			join_list.add(String.format("and pit.uuid = '%s'", id_type_uuid));
		}
		
		where_list.add(String.format("pi.identifier like '%%%s%%'", id));
	}
	
	public void addCriteriaAddress(String address) {
		address = scrub_name(address);
		
		select_list.add("concat_ws(' ', COALESCE(pad.address1, ''), COALESCE(pad.country, '')) AS address");
		
		where_list
		        .add(String
		                .format(
		                    "(concat_ws(' ', COALESCE(pad.address1, ''), COALESCE(pad.address2, ''), COALESCE(pad.country, '')) LIKE '%%%s%%' OR concat_ws(' ', COALESCE(pad.city_village, ''), COALESCE(pad.state_province, '')) LIKE '%%%s%%')",
		                    address, address));
	}
	
	public void addCriteriaVisitDate(Date visit_date_from, Date visit_date_to) throws Exception {
		if (!visit_date_from.after(visit_date_to) && visit_date_from.after(df.parse("1900-01-01"))) {
			Date today = new Date();
			if (visit_date_to.after(today))
				visit_date_to = today;
			where_list
			        .add(String
			                .format(
			                    "((v.date_stopped is NULL or v.date_stopped >= date_format('%s', '%%Y-%%m-%%d 00:00:00')) and v.date_started <= date_format('%s', '%%Y-%%m-%%d 23:59:59'))",
			                    df.format(visit_date_from), df.format(visit_date_to)));
		} else {
			throw new Exception(String.format("Date range not valid: %s to %s", df.format(visit_date_from),
			    df.format(visit_date_to)));
		}
	}
	
	public void addCriteriaProvider(String provider_uuid) {
		provider_uuid = scrub_uuid(provider_uuid);
		
		if (has_encounter_join == 0) {
			join_list.add("join encounter as e on e.visit_id = v.visit_id and e.voided is false");
			has_encounter_join = 1;
		}
		join_list.add("join encounter_provider as ep on ep.encounter_id = e.encounter_id");
		join_list.add("join provider as prov on prov.provider_id = ep.provider_id");
		
		where_list.add(String.format("prov.uuid = '%s'", provider_uuid));
	}
	
	public void addCriteriaDiagOrObs(String diag_or_obs_uuid) {
		diag_or_obs_uuid = scrub_uuid(diag_or_obs_uuid);
		
		if (has_encounter_join == 0) {
			join_list.add("join encounter as e on e.visit_id = v.visit_id and e.voided is false");
			has_encounter_join = 1;
		}
		join_list.add("join obs on obs.encounter_id = e.encounter_id and obs.voided is false");
		join_list.add("join concept as obs_c on obs_c.concept_id = obs.value_coded");
		
		where_list.add(String.format("obs_c.uuid = '%s'", diag_or_obs_uuid));
	}
	
	public JSONObject search() throws Exception {
		if (where_list.isEmpty())
			throw new Exception("Empty Search");
		String select_string = select_list.isEmpty() ? "" : "," + String.join(",\n", select_list);
		String join_string = String.join("\n", join_list);
		String where_string = String.join("\nAND\n", where_list);
		
		String rquery = query.replace("%SELECT_LIST%", select_string).replace("%JOIN_LIST%", join_string)
		        .replace("%WHERE_LIST%", where_string);
		
		ResultSet resultSet;
		try {
			Connection conn = DatabaseUpdater.getConnection();
			PreparedStatement statement = conn.prepareStatement(rquery);
			resultSet = statement.executeQuery();
		}
		catch (Exception e) {
			throw new Exception("search failed to execute query");
		}
		
		JSONObject result;
		try {
			JSONArray json_results = convertToJSON(resultSet);
			result = new JSONObject();
			result.put("pageOfResults", json_results);
			result.put("totalCount", json_results.length());
		}
		catch (Exception e) {
			throw new Exception("search failed to execute query");
		}
		return result;
	}
	
	/**
	 * Taken from
	 * http://biercoff.com/nice-and-simple-converter-of-java-resultset-into-jsonarray-or-xml/
	 * Convert a result set into a JSON Array
	 * 
	 * @param resultSet
	 * @return a JSONArray
	 * @throws Exception
	 */
	public static JSONArray convertToJSON(ResultSet resultSet) throws Exception {
		JSONArray jsonArray = new JSONArray();
		while (resultSet.next()) {
			int total_rows = resultSet.getMetaData().getColumnCount();
			JSONObject obj = new JSONObject();
			for (int i = 0; i < total_rows; i++) {
				obj.put(resultSet.getMetaData().getColumnLabel(i + 1), resultSet.getObject(i + 1));
			}
			jsonArray.put(obj);
		}
		return jsonArray;
	}
}
