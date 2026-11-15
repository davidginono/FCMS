-- Public, minimal read contracts owned by IAM and client modules.
create view iam_staff_directory as select id,first_name,last_name from users;
create view client_saccos_directory as select id,saccos_name,visible from saccos;
