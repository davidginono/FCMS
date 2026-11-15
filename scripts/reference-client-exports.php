<?php
// Original presentation slices with synthetic rows only; no SQL/authentication entrypoints run.
$case = $argv[1];
$reference = $argv[2] ?? '/reference';
$proof = $argv[3] ?? '/proof';
$input = json_decode(file_get_contents($proof.'/source-'.$case.'.json'), true);
$kind = $input['kind'];
require $reference.'/fpdf_report_helpers.php';
$helper = file_get_contents($reference.'/fpdf_report_helpers.php');
$helper = substr($helper, strpos($helper, 'class ReportPDF'));
$helper = str_replace(['class ReportPDF', "'Page '"], ['class LocalizedReportPDF', '$GLOBALS["input"]["pageLabel"]." "'], $helper);
eval($helper);
$files = ['CLIENT_SACCOS'=>'print_saccos.php', 'CLIENT_AMCOS_REGISTER'=>'print_amcos.php',
    'CLIENT_UNIONS_REGISTER'=>'print_union.php', 'CLIENT_MICROFINANCE_REGISTER'=>'print_microfinance.php',
    'CLIENT_OTHERS_REGISTER'=>'print_otherclients.php'];
$original = file_get_contents($reference.'/'.$files[$kind]);
$keys = match ($kind) {
    'CLIENT_SACCOS' => [null,'saccos_name','Entry_Date','expire_date','Region','Annual_fee','Amount','datepaid','Training_fee','Sys_install_fee','Sys_reinstall_fee','Android_fee','bulk_sms','fyear','Name'],
    'CLIENT_AMCOS_REGISTER' => [null,'Amcos_name','Entry_Date','expire_date','Region','pfile','Annual_fee','Amount','Programmer_cost','Sys_install_fee','Training_fee','Android_fee','Sys_reinstall_fee','bulk_sms','hosting_fee','fyear','Name'],
    'CLIENT_UNIONS_REGISTER' => [null,'union_name','Entry_Date','expire_date','Region','pfile','Annual_fee','Amount','tax','com','Programmer_cost','Sys_install_fee','Training_fee','Android_fee','Sys_reinstall_fee','bulk_sms','hosting_fee','fyear','Name'],
    default => [null,$kind === 'CLIENT_OTHERS_REGISTER'?'Client_Name':'Microfinance_Name','Entry_Date','expire_date','Region','Annual_fee','Amount','Training_fee','Sys_install_fee','Sys_reinstall_fee','Android_fee','fyear','snames']
};
$rows = [];
foreach ($input['rows'] as $values) {
    $row = [];
    foreach ($keys as $index=>$key) {
        if ($key === null) continue;
        $value = $values[$index];
        if ($key === 'pfile') $value = $value === 'Yes' || $value === 'Ndiyo' ? 'synthetic.pdf' : '';
        $row[$key] = $value;
    }
    $rows[] = $row;
}
class FixtureRows {
    private $cursor = 0;
    public $num_rows;
    function __construct(private $rows) { $this->num_rows = count($rows); }
    function fetch_assoc() { return $this->rows[$this->cursor++] ?? null; }
}
function fixture_next_row($result) { return $result->fetch_assoc(); }
function fixture_num_rows($result) { return $result->num_rows; }
$result = new FixtureRows($rows);
if ($kind === 'CLIENT_SACCOS') {
    $width_cell = $input['widths'];
    $class = substr($original, strpos($original, 'class PDF'), strpos($original, '// Safe number format helper') - strpos($original, 'class PDF'));
    $class = preg_replace('/\$this->PrintTableHeader\(array\(.*?\), \$width_cell, true\);/s', '$this->PrintTableHeader($GLOBALS["input"]["columns"], $width_cell, true);', $class);
    eval($class);
    function safe_number_format($value) { return number_format((float)$value,2); }
}
$a = strpos($original, '$pdf = new ');
$code = substr($original, $a, strpos($original, '$pdf->Output', $a)-$a);
$code = str_replace('new ReportPDF(', 'new LocalizedReportPDF(', $code);
// Replace only data access envelopes, before evaluating the isolated presentation.
$code = preg_replace('/\$result = (?:mysqli_query\(\$con, \$sql\)|\$con->query\(\$sql\));/', '', $code);
$code = preg_replace('/\$yearResult = .*?\$selected_year_label = .*?;/s', '', $code);
$code = str_replace('$con->close();', '', $code);
$code = str_replace(['mysqli_fetch_assoc(', 'mysqli_num_rows('], ['fixture_next_row(', 'fixture_num_rows('], $code);
// Prepared localized captions and date/decimal strings retain original geometry.
$code = preg_replace('/\$pdf->PrintTableHeader\(array\(.*?\), \$width_cell, true\);/s', '$pdf->PrintTableHeader($input["columns"], $width_cell, true);', $code);
$code = str_replace('$columns = $pdf->PrintColumnsHeader($columns);', 'foreach($columns as $i=>&$column) $column["label"]=$input["columns"][$i]; unset($column); $columns = $pdf->PrintColumnsHeader($columns);', $code);
$code = preg_replace('/\x27(?:AMCOS|UNION) REPORT\x27/', '$input["title"]', $code);
$code = str_replace("'OTHER CLIENTS FOR YEAR: ' . htmlspecialchars(\$selected_year_label)", '$input["title"]', $code);
$code = str_replace("empty(\$row['pfile']) ? 'No' : 'Yes'", '$input["rows"][$no-1][5]', $code);
$code = preg_replace('/number_format\((\(float\)\$row\[\$column\[\x27key\x27\]\])\)/', 'number_format($1,2)', $code);
$code = preg_replace('/number_format\((\$total_[a-z_]+)\)/', 'number_format($1,2)', $code);
$code = str_replace("10, 'Total', 1", '10, $input["totals"][0], 1', $code);
$code = str_replace(["'No records found for the selected year.'", "'No records found'"], '$input["emptyMessage"]', $code);
eval($code);
$pdf->Output('F', $proof.'/original-'.$case.'.pdf');
