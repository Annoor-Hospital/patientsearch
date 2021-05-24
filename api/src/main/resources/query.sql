select
    ppi.identifier as identifier,
    concat_ws(' ', pn.given_name, COALESCE(pn.middle_name,''), COALESCE(pn.family_name,'')) as name,
    concat_ws(' ',COALESCE(pa1.value,''),COALESCE(pa2.value,''),COALESCE(pa3.value,'')) as arabic_name,
    DATE_FORMAT(NOW(), '%Y') - DATE_FORMAT(p.birthdate, '%Y') - (DATE_FORMAT(NOW(), '00-%m-%d') < DATE_FORMAT(p.birthdate, '00-%m-%d')) AS age,
    # p.birthdate as birthdate,
    # p.gender as gender,
    # concat_ws(' ', COALESCE(pad.address1, ''), COALESCE(pad.address2, ''), COALESCE(pad.country, '')) as location_english,
    # concat_ws(' ', COALESCE(pad.city_village, ''), COALESCE(pad.state_province, '')) as location_arabic,
    max(v.date_started) as visit_date,
    p.uuid as uuid,
    IF(va.value_reference = 'Admitted', 'true', 'false') as hasBeenAdmitted
    %SELECT_LIST%
from person as p
    join person_name as pn on p.person_id = pn.person_id and pn.voided is false
    left join person_address as pad on pad.person_id = p.person_id
    
    left join person_attribute pa1 on pa1.person_id = p.person_id and pa1.person_attribute_type_id = (
        select person_attribute_type_id from person_attribute_type where name='givenNameLocal'
    ) and pa1.voided = 0
    left join person_attribute pa2 on pa2.person_id = p.person_id and pa2.person_attribute_type_id = (
        select person_attribute_type_id from person_attribute_type where name='middleNameLocal'
    ) and pa2.voided = 0
    left join person_attribute pa3 on pa3.person_id = p.person_id and pa3.person_attribute_type_id = (
        select person_attribute_type_id from person_attribute_type where name='familyNameLocal'
    ) and pa3.voided = 0
    
    join patient_identifier ppi on p.person_id = ppi.patient_id
    join patient_identifier_type ppit on ppi.identifier_type = ppit.patient_identifier_type_id and ppit.uuid = (
        select gp.property_value from global_property as gp where gp.property='bahmni.primaryIdentifierType' limit 1
    )
    
    left join visit v on v.patient_id = p.person_id and v.voided is false
    
    left join visit as v_in on v_in.patient_id = p.person_id and v_in.voided is false and v_in.date_stopped is NULL
    left join visit_attribute as va on va.visit_id = v_in.visit_id and va.voided is false and va.attribute_type_id = (
        select visit_attribute_type_id from visit_attribute_type where name='Admission Status'
    )
    %JOIN_LIST%
where
    %WHERE_LIST%
group by p.person_id;