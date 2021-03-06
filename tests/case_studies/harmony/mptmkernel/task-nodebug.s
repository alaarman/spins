/* 
Harmony Validation
File :              task
Author :            T.Cattel cattel@iit.nrc.ca
Creation :          3 Jan 94
Last modification : 25 Jul 94
Description :       
- general skeleton of application task with fine grain control
for task destruction
*/

proctype Task(byte _Active){
  byte  ltm_id,rid,requestor,child,child1,child2;
  byte  leng,burst,replyee,sender,i,corr_id;
  short level;
  message(request);message(response);message(reply);
  
START:
  (state[_Active]==READY && valid[_Active]) ->
end:
progress:
    do
    :: atomic{
         ( _Active==running[processor[_Active]] &&
           !treatment[processor[_Active]] &&
           (!len(fifo[processor[_Active]]) || masked[_Active]))
         ->
         if
         :: killed[_Active] ->

#include "infanticides"

         :: !killed[_Active] ->
              if
              :: index[_Active]==TASK1 -> /* Task1 */

#include "valid/suicide"

              :: index[_Active]==TASK2 -> /* Task2 */

#include "valid/childparicid"

              :: index[_Active]==TASK3 -> /* Task3 */ 

#include "valid/R1"

              :: index[_Active]==TASK4 -> /* Task4 */ 

#include "valid/K1"

              :: index[_Active]>TASK4 ->  
                   assert(0)

            fi
         fi
       }
    od
}