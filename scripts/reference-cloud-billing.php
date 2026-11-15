<?php
// Isolated paid-cloud presentation only; native filters retain actual fees/costs, including unpaid.
$case=$argv[1];$reference=$argv[2]??'/reference';$proof=$argv[3]??'/proof';
$input=json_decode(file_get_contents($proof.'/source-'.$case.'.json'),true);
require $reference.'/fpdf_report_helpers.php';
$original=file_get_contents($reference.'/print_paid_cloud_backup.php');
$a=strpos($original,'class PDF');$b=strpos($original,'$selected_year =');$class=substr($original,$a,$b-$a);
$class=str_replace("'Page '",'$GLOBALS["input"]["pageLabel"]." "',$class);
$class=str_replace("'/{nb}   |   Printed: ' . \$date",'"/{nb}".$GLOBALS["input"]["footer"]["suffix"]',$class);eval($class);
class BillingRows {private $i=0;function __construct(private $rows){}function next(){return $this->rows[$this->i++]??null;}}
$rows=[];foreach($input['rows'] as $row)$rows[]=['saccos_name'=>$row[1],'cloud_backup'=>str_replace(',','',$row[2]),'backup_cost'=>str_replace(',','',$row[3]),'fyear'=>$row[4]];
$result=new BillingRows($rows);function fixture_next_row($result){return $result->next();}
$a=strpos($original,'$pdf = new PDF');$b=strpos($original,'$query =',$a);$body=strpos($original,"$".'pdf->SetFont(\'Arial\', \'\', 10);',$b);
if($a===false||$b===false||$body===false)throw new RuntimeException('Presentation boundary changed');
$code=substr($original,$a,$b-$a).substr($original,$body,strpos($original,'$pdf->Output',$body)-$body);
$code=str_replace("'Paid Cloud Backup - Saccos'",'$input["title"]',$code);
$code=preg_replace('/\$pdf->PrintTableHeader\(array\(.*?\), \$exportHeaderWidths, false\);/s','$pdf->PrintTableHeader($input["columns"],$exportHeaderWidths,false);',$code);
$code=str_replace('mysqli_fetch_assoc(','fixture_next_row(',$code);
$code=preg_replace('/number_format\((\$[a-zA-Z]+)\)/','number_format($1,2)',$code);
$code=str_replace("'Total'",'$input["totals"][0]',$code);
if(preg_match('/mysqli_(?:query|real_escape_string|error)|\$con\s*->|\b(?:require|include)(?:_once)?\s*[(\x27\x22]/',$code))throw new RuntimeException('Non-presentation code rejected');
eval($code);$pdf->Output('F',$proof.'/original-'.$case.'.pdf');
