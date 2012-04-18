mtype = {A, B, I, Na, Nb, Ng, R};

#define k(x1)           if \
			:: (x1 == Na)-> kNa=1 \
                        :: (x1 == Nb)-> kNb=1 \
			:: else skip \
                        fi; 

#define k2(x1,x2)	if \
			:: (x1 == Nb && x2==B)-> k_Nb__B=1        \
                        :: else skip   \
                        fi

#define k3(x1,x2,x3)    if \
			:: (x1 == Na && x2 == A && x3 == B)-> k_Na_A__B=1 \
                        :: else skip \
			fi

#define k4(x1,x2,x3,x4)	if \
			:: (x1==Na && x2==Nb && x3==B && x4==A)-> k_Na_Nb_B__A=1 \
                        :: else skip \
			fi

#define IniRunning(x,y) if      \
			:: ((x==A)&&(y==B))-> IniRunningAB=1 \
			:: else-> skip \
			fi
#define IniCommit(x,y) if      \
			:: ((x==A)&&(y==B))-> IniCommitAB=1 \
			:: else-> skip \
			fi
#define ResRunning(x,y) if      \
			:: ((x==A)&&(y==B))-> ResRunningAB=1 \
			:: else-> skip \
			fi
#define ResCommit(x,y) if      \
			:: ((x==A)&&(y==B))-> ResCommitAB=1 \
			:: else-> skip \
			fi

bit IniRunningAB=0;
bit IniCommitAB=0;
bit ResRunningAB=0;
bit ResCommitAB=0;

/* hidden byte changes; */

/********************************************
 *
 * Channels: 
 *       ca: type 1 messages {x1,x2}PK(x3) 
 *       cb: type 2 messages {x1}PK(x2)
 *	 cc: type 3 messages {x1,x2,x3}PK(x4)
 * 
 ********************************************/

chan ca = [0] of {mtype, mtype, mtype, mtype}; 
chan cb = [0] of {mtype, mtype, mtype};
chan cc = [0] of {mtype, mtype, mtype, mtype, mtype};

/* Initiator */
proctype PIni (mtype self; mtype party; mtype nonce) 
{ 	
	mtype g1;
	
	atomic { 
        IniRunning(self,party);
        ca ! self, nonce, self, party; 
      }
	
end1:	
      atomic { 
        cc ? eval (self), eval (nonce), g1, eval(party), eval (self);
	  IniCommit(self,party);

	  cb ! self, g1, party;
      }
}

/* Responder */
proctype PRes (mtype self; mtype nonce) 
{ 	
	mtype g2, g3; 

end2:
	atomic {	
	  ca ? eval (self), g2, g3, eval (self);
	  ResRunning(g3,self);

	  cc ! self, g2, nonce, self, g3;
	}
end3:
	atomic {
	  cb ? eval (self), eval (nonce), eval (self);
	  ResCommit(g3,self);
	}
}

proctype PI () 
{
      		/*         Intruder always knows A, B, I, Ng; */

      bit kNa = 0;        /* Intruder knows 	Na */
      bit kNb = 0;        /* Intruder knows 	Nb */

      bit k_Na_Nb_B__A = 0; /*     "       "     {Na, Nb, B}{PK(A)} */
      bit k_Na_A__B = 0;  /*     "       "     {Na, A}{PK(B)} */
      bit k_Nb__B = 0;    /*     "       "     {Nb}{PK(B)} */

	mtype x1=0,x2=0,x3=0,x4=0;


end4:
	do
        ::   cc ! (kNa -> A : R), Na, Na, B, A
        ::   cc ! (((kNa && kNb) || k_Na_Nb_B__A )-> A : R), Na, Nb, B, A
        ::   cc ! (kNa -> A : R), Na, Ng, B, A
        ::   cc ! (kNa -> A : R), Na, A, B, A
        ::   cc ! (kNa -> A : R), Na, B, B, A
        ::   cc ! (kNa -> A : R), Na, I, B, A

        ::   cc ! (kNa -> A : R), Na, Na, I, A
        ::   cc ! ((kNa && kNb) -> A : R), Na, Nb, I, A
        ::   cc ! (kNa -> A : R), Na, Ng, I, A
        ::   cc ! (kNa -> A : R), Na, A, I, A
        ::   cc ! (kNa -> A : R), Na, B, I, A
        ::   cc ! (kNa -> A : R), Na, I, I, A

        ::   ca ! ((kNa || k_Na_A__B) -> B : R), Na, A, B
        ::   ca ! (kNa -> B : R), Na, B, B
        ::   ca ! (kNa -> B : R), Na, I, B
        ::   ca ! (kNb -> B : R), Nb, A, B
        ::   ca ! (kNb -> B : R), Nb, B, B
        ::   ca ! (kNb -> B : R), Nb, I, B

        ::   ca ! B, Ng, A, B
        ::   ca ! B, Ng, B, B
        ::   ca ! B, Ng, I, B

        ::   ca ! B, A, A, B
        ::   ca ! B, A, B, B
        ::   ca ! B, A, I, B

        ::   ca ! B, B, A, B
        ::   ca ! B, B, B, B
        ::   ca ! B, B, I, B

        ::   ca ! B, I, A, B
        ::   ca ! B, I, B, B
        ::   ca ! B, I, I, B

        ::   cb ! ((kNb || k_Nb__B) -> B : R), Nb, B 

        :: d_step {
             ca ? _, x1, x2, x3; 	if
					:: (x3==I)-> k(x1); k(x2)
					:: else k3(x1,x2,x3)
					fi;
					x1 = 0;
					x2 = 0;
					x3 = 0; 
	     }

        :: d_step { 
		cb ? _, x1, x2; 	if
					:: (x2==I)-> k(x1)
					:: else k2(x1,x2)
					fi;
					x1 = 0;
					x2 = 0;
	     }

       :: d_step {
             cc ? _, x1, x2, x3, x4; 	if
					:: (x4==I)-> k(x1); k(x2); k(x3)
					:: else k4(x1,x2,x3,x4)
					fi;
					x1 = 0;
					x2 = 0;
					x3 = 0;
					x4 = 0; 
	     }

	od
}

init
{
  atomic {
      if 
      :: run PIni (A, I, Na)
      :: run PIni (A, B, Na)
      fi;

	run PRes (B, Nb);

	run PI ();
  }
}

#define __instances_PIni 1
#define __instances_PRes 1
#define __instances_PI 1

