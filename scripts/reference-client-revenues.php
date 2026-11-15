<?php
// Original presentation only. Inputs are prepared synthetic financial DTO values, never SQL.
$case=$argv[1];$reference=$argv[2]??'/reference';$proof=$argv[3]??'/proof';
$input=json_decode(file_get_contents($proof.'/source-'.$case.'.json'),true);
$kind=$input['kind'];
require $reference.'/fpdf_report_helpers.php';
class SyntheticRows {
 private $cursor=0;
 function __construct(private $rows) {}
 function fetch_assoc() {return $this->rows[$this->cursor++]??null;}
}
function fixture_next_row($result) {return $result->fetch_assoc();}
$rows=[];
if($kind==='CLIENT_PAYMENT') {
 $paid=str_contains($case,'_PAID-');
 $original=file_get_contents($reference.'/'.($paid?'print_paidsaccos.php':'print_unpaidsaccos.php'));
 $a=strpos($original,'class PDF');$b=strpos($original,'$pdf = new PDF');
 // Class block ends before presentation; exclude the intervening request/SQL envelopes.
 $class=substr($original,$a,strpos($original,"\n}",$a)+2-$a);
 $class=str_replace("'Page '",'$GLOBALS["input"]["pageLabel"]." "',$class);
 $class=str_replace("'/{nb}   |   Printed: ' . \$date",'"/{nb}".$GLOBALS["input"]["footer"]["suffix"]',$class);
 eval($class);
 foreach($input['rows'] as $r)$rows[]=['saccos_name'=>$r[1],'Amount'=>$r[2],'Annual_fee'=>$r[2],'fyear'=>$r[3]];
 $result=new SyntheticRows($rows);$selected_year_label='2026';
 $body=strpos($original,'// Table Body');
 $code=substr($original,$b,strpos($original,'// Fetch data',$b)-$b)
     .substr($original,$body,strpos($original,'$pdf->Output',$body)-$body);
 $code=preg_replace('/\$pdf->Cell\(0, 10, .*?, 0, 1, \'C\'\);/','$pdf->Cell(0,10,$input["title"],0,1,"C");',$code);
 $code=preg_replace('/\$pdf->PrintTableHeader\(array\(.*?\), \$exportHeaderWidths, false\);/s','$pdf->PrintTableHeader($input["columns"],$exportHeaderWidths,false);',$code);
} elseif($kind==='CLIENT_REVENUE'||$kind==='CLIENT_SINGLE') {
 $original=file_get_contents($reference.'/print_saccosreport.php');
 $a=strpos($original,'$singleColumnReports = array(');$b=strpos($original,'if (empty($selected_year))',$a);eval(substr($original,$a,$b-$a));
 $report_type=$kind==='CLIENT_SINGLE'?'training_fee':'full';
 $keys=$kind==='CLIENT_SINGLE'?[null,'saccos_name','Training_fee']:[null,'saccos_name','Annual_fee','Amount','tax','com','Programmer_cost','Sys_install_fee','Training_fee','Android_fee','android_cost','Sys_reinstall_fee','bulk_sms','hosting_fee','cloud_backup','backup_cost','Revenue_Total','Gross_Profit'];
 foreach($input['rows'] as $values){$row=[];foreach($keys as $i=>$key)if($key!==null)$row[$key]=$values[$i];$rows[]=$row;}
 $selected_year='2026';$selected_status='all';$date_range_label=$input['period'];
 $a=strpos($original,'$pdf = new TableReportPDF');$branch=strpos($original,'if (isset($singleColumnReports',$a);
 $code=substr($original,$a,$branch-$a);
 if($kind==='CLIENT_SINGLE') {
  $a=strpos($original,'$config = $singleColumnReports',$branch);$code.=substr($original,$a,strpos($original,'$pdf->Output',$a)-$a);
  $code=str_replace("\$config['title'] . htmlspecialchars(\$selected_year)",'$input["title"]',$code);
  $code=str_replace("array('No', 'Saccos Name', \$config['label'])",'$input["columns"]',$code);
 } else {
  $a=strpos($original,'$columns = array(',$branch);$code.=substr($original,$a,strrpos($original,'$pdf->Output')-$a);
  $code=preg_replace('/\x27SACCOS REPORT FOR YEAR: \x27 .*?\$status_heading/','$input["title"]',$code);
 }
} else {
 $wide=str_contains($kind,'UNIONS')||str_contains($kind,'AMCOS');
 $original=file_get_contents($reference.'/'.($wide?'print_unionreport.php':(str_contains($kind,'MICROFINANCE')?'print_microfinance_report.php':'print_other_report.php')));
 $keys=$wide?[null,'union_name','Annual_fee','Amount','tax','com','Programmer_cost','Sys_install_fee','Training_fee','Android_fee','Sys_reinstall_fee','bulk_sms','hosting_fee','Revenue_Total','Profit']:[null,str_contains($kind,'MICROFINANCE')?'Microfinance_Name':'Client_Name','Annual_fee','Amount','Programmer_cost','Sys_install_fee','Training_fee','Android_fee','Sys_reinstall_fee','Revenue_Total','Profit'];
 foreach($input['rows'] as $values){$row=[];foreach($keys as $i=>$key)if($key!==null)$row[$key]=$values[$i];$rows[]=$row;}
 $result=$result1=new SyntheticRows($rows);$selected_year='2026';$date_range_label=$input['period'];
 $a=strpos($original,'$pdf = new TableReportPDF');$code=substr($original,$a,strpos($original,'$pdf->Output',$a)-$a);
 if(str_contains($kind,'OTHERS')) {
  $start=strpos($code,'// Sanitize and validate input');$end=strpos($code,'// Print the selected year as heading');
  if($start===false||$end===false)throw new RuntimeException('Presentation boundary changed');
  $code=substr($code,0,$start).substr($code,$end);
 }
 $code=preg_replace('/\x27 ?(?:UNION REPORT FOR:|MICROFINANCE REPORT FOR YEAR:|OTHER CLIENTS REPORT FOR YEAR:) \x27 \. htmlspecialchars\(\$selected_year\)/','$input["title"]',$code);
 $code=preg_replace('/\$pdf->PrintTableHeader\(array\(.*?\), \$exportHeaderWidths, false\);/s','$pdf->PrintTableHeader($input["columns"],$exportHeaderWidths,false);',$code);
 // FCMS revenue is received money; an invoice is not received income.
 $code=str_replace('$total_fee = $annual_fee +','$total_fee = $amount +',$code);
 $code=preg_replace('/, \$(annual_fee|amount|programmer_cost|sys_install_fee|training_fee|android_fee|sys_reinstall_fee|total_fee|profit|total_[a-z_]+), /',', number_format($$1,2), ',$code);
}
$code=str_replace('$columns = $pdf->PrintColumnsHeader($columns);','foreach($columns as $i=>&$column)$column["label"]=$input["columns"][$i];unset($column);$columns=$pdf->PrintColumnsHeader($columns);',$code);
$code=str_replace('mysqli_fetch_assoc(', 'fixture_next_row(', $code);
$code=preg_replace('/number_format\((\$[a-z_]+)\)/','number_format($1,2)',$code);
$code=str_replace("number_format(\$totals[\$column['key']])","number_format(\$totals[\$column['key']],2)",$code);
$code=str_replace(["'Grand Total'","'Total'"],['$input["totals"][0]','$input["totals"][0]'],$code);
if(preg_match('/mysqli_(?:query|real_escape_string|error)|\$con\s*->|\b(?:require|include)(?:_once)?\s*[(\x27\x22]/',$code))throw new RuntimeException('Non-presentation code rejected');
eval($code);
$pdf->Output('F',$proof.'/original-'.$case.'.pdf');
