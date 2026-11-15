<?php
// Isolated original presentation only: no connect.php, authentication, SQL or real data.
$reference=$argv[2] ?? '/reference';
$proof=$argv[3] ?? '/proof';
require $reference.'/fpdf_report_helpers.php';
$case=$argv[1];
$input=json_decode(file_get_contents($proof.'/source-'.$case.'.json'),true);
$kind=$input['kind'];
$cursor=0;
function fixture_next_row($ignored) {
 global $cursor,$input,$kind;
 if($cursor>=count($input['rows']))return null;
 $r=$input['rows'][$cursor++];
 if($kind==='EXPENSE_CATEGORIES') return ['category'=>$r[1],'total_amount'=>str_replace(',','',$r[2])];
 if($kind==='REPORT_STAFF') return ['Saccos_Name'=>$r[1],'date'=>$r[2],'end_date'=>$r[3],'report_status'=>$r[4],'activities'=>$r[5]];
 if($kind==='REPORT_CLIENT') return ['fname'=>$r[1],'Saccos_Name'=>$r[2],'date'=>$r[3],'end_date'=>$r[4],'report_status'=>$r[5],'activities'=>$r[6]];
 if($kind==='TASK')return ['fname'=>$r[1],'tnames'=>$r[2],'Saccos_Name'=>$r[3],'start_date'=>$r[4],'end_date'=>$r[5],'activities'=>$r[6],'Allowance'=>$r[7],'Amount'=>$r[8]];
 return ['category'=>$r[1],'date_created'=>'2026-11-01','description'=>$r[3],'amount'=>$r[4]];
}
$result=$result1=null;$fromdate='2026-11-01';$todate='2026-11-02';$date_range_label=$input['period'];
if($kind==='EXPENSE_CATEGORIES') {
 $original=file_get_contents($reference.'/print_all_cat.php');
 $a=strpos($original,'$pdf = new TableReportPDF');$b=strpos($original,'// Output the PDF');
 $code=substr($original,$a,$b-$a);
 $fyear='2026';$grandTotal=111.43;
 $code=str_replace("'Category Report for Year ' . htmlspecialchars(\$fyear)",'$input["headings"][0]["text"]',$code);
 $code=str_replace("'Total  Amount: ' . htmlspecialchars(number_format(\$grandTotal, 2))",'$input["headings"][1]["text"]',$code);
 $code=str_replace("array('#', 'Category Name', 'Total Amount')",'$input["columns"]',$code);
} elseif(str_starts_with($kind,'REPORT_')) {
 $original=file_get_contents($reference.'/'.($kind==='REPORT_STAFF'?'print_reportbystaff.php':'print_staff_report.php'));
 $a=strpos($original,'class MyPDF');$b=strpos($original,'$pdf = new MyPDF');
 $class=substr($original,$a,$b-$a);
 $class=str_replace("'Page '",'$GLOBALS["input"]["pageLabel"]." "',$class);
 if($kind==='REPORT_CLIENT') {
  $class=str_replace("'Client Name: ' . \$this->client_name",'$GLOBALS["input"]["headings"][0]["text"]',$class);
  $class=str_replace("'SUMMARY OF SOFTWARE ACTIVITIES IN SACCOS'",'$GLOBALS["input"]["headings"][1]["text"]',$class);
 }
 eval($class);
 $date_range_label=$input['headings'][$kind==='REPORT_STAFF'?3:2]['text'];
 $client_name='';$staff_name='';
 $code=substr($original,$b,strpos($original,'$pdf->Output',$b)-$b);
 $companyIndex=$kind==='REPORT_STAFF'?0:3;
 $code=str_replace("'COMPUTER RESOURCES (T) LIMITED'",'$input["headings"]['.$companyIndex.']["text"]',$code);
 $code=str_replace("'SUMMARY OF SOFTWARE ACTIVITIES IN SACCOS'",'$input["headings"][1]["text"]',$code);
 $code=str_replace('"STAFF NAME: {$staff_name}"','$input["headings"][2]["text"]',$code);
 $code=preg_replace('/\$pdf->PrintTableHeader\(array\(.*?\), \$exportHeaderWidths, true\);/s','$pdf->PrintTableHeader($input["columns"], $exportHeaderWidths, true);',$code);
} else {
$original=file_get_contents($reference.'/'.($kind==='TASK'?'print_task_report.php':'print_expenses_report.php'));
if($kind==='TASK') {
 $a=strpos($original,'class PDF');$b=strpos($original,'// Create PDF instance');
 eval(substr($original,$a,$b-$a));
 $a=strpos($original,'$pdf = new PDF');$b=strpos($original,'// Fetch data from database');
 $code=substr($original,$a,$b-$a);
 $a=strpos($original,'// Initialize total variables');$b=strpos($original,'// End output buffering');
 $code.=substr($original,$a,$b-$a);
} else {
 $a=strpos($original,'$pdf = new TableReportPDF');$b=strpos($original,'// Footer');
 $code=substr($original,$a,$b-$a);
}
}
eval(str_replace('mysqli_fetch_assoc(', 'fixture_next_row(', $code));
$pdf->Output('F',$proof.'/original-'.$case.'.pdf');
