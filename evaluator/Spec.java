
// file: Spec.java

package evaluator;

import java.util.Iterator;

/**
* Stack-effect (specification).
* @author Jaanus Poial
* @version 0.6
* @since 1.5
*/
public class Spec {

   /** workfield to keep maximal position index */
   int maxPosIndex;
   /** "home" type system */
   TypeSystem ts;
   /** for scanner words */
   String parseString;
   /** list of input parameter types */
   Tvector leftSide;
   /** list of output parameter types */
   Tvector rightSide;

   Spec (Tvector left, Tvector right, TypeSystem tsys,
     String parse, int np) {
      leftSide = left;
      rightSide = right;
      ts = tsys;
      parseString = parse;
      maxPosIndex = np;
   } // end of constructor

   Spec (TypeSystem tsys) {
      this (new Tvector(), new Tvector(), tsys, "", 0);
   } // end of constructor

   /**
    * Finds greatest lower bound of given stack effects.
    */
   public Spec glb (Spec t, TypeSystem tsys, SpecSet ss) {
      if (t==null) return null;
      int n1 = leftSide.size();
      int n2 = rightSide.size();
      int m1 = t.leftSide.size();
      int m2 = t.rightSide.size();
      Spec slong = null;
      Spec sshort = null;
      int plen = 0;
      if (n1 > m1) {
         slong = (Spec)this.clone();
         sshort = (Spec)t.clone();
         plen = n1 - m1;
         if ((n2 - m2) != plen) return null;
      } else {
         slong = (Spec)t.clone();
         sshort = (Spec)this.clone();
         plen = m1 - n1;
         if ((m2 - n2) != plen) return null;
      }
      Spec result = slong.cprefix (plen, tsys, ss);
      if (result != null) result = result.unify (sshort, tsys, ss);
      return result;
   } // end of glb()

   /**
    * Returns nearest idempotent for this spec (usually null!!).
    */
   public Spec idemp (TypeSystem tsys, SpecSet ss) {
      int n1 = leftSide.size();
      int n2 = rightSide.size();
      if (n1 != n2) return null;
      Spec result = (Spec)this.clone();
      result = result.cprefix (n1, tsys, ss);
      return result;
   } // end of idemp()

   /**
    * Returns the Pi-star function value of this spec.
    */
   public Spec piStar (TypeSystem tsys, SpecSet ss) {
      SpecList sqlist = new SpecList();
      sqlist.add ((Spec)clone());
      sqlist.add ((Spec)clone());
      return glb (sqlist.evaluate (tsys, ss), tsys, ss);
   } // end of piStar()

   /**
    * Unify len first symbols of leftside and rightside,
    * return null if it is impossible.
    */
   Spec cprefix (int len, TypeSystem ts, SpecSet ss) {
      Spec result = (Spec)this.clone();
      if (len > 0) {
         int n1 = leftSide.size();
         int n2 = rightSide.size();
         if (n1 < len) return null;
         if (n2 < len) return null;
         result.incrementWild (0);
         result.maxPosIndex = result.maxPos();
         for (int i=0; i<len; i++) {
            TypeSymbol m1 = (TypeSymbol)result.leftSide.get (i);
            TypeSymbol m2 = (TypeSymbol)result.rightSide.get (i);
            int rel = ts.relation (m1.ftype, m2.ftype);
            TypeSymbol mnew = null;        
            switch (rel) {
               case 0: // type conflict
                  return null;
               case 1: // m1 win
                  mnew = new TypeSymbol (m1.ftype, ++result.maxPosIndex);
                  break;
               case 2: // m2 win
                  mnew = new TypeSymbol (m2.ftype, ++result.maxPosIndex);
                  break;
               case 3: // equal types
                  mnew = new TypeSymbol (m1.ftype, ++result.maxPosIndex);
                  break;
               default: throw new RuntimeException (" no relation!!!");
            }
            result.substitute (m1, mnew);
            result.substitute (m2, mnew);
         }
      }
      SpecList tmp = new SpecList();
      tmp.add (result);
      result = tmp.normalize (result, ts, ss);
      return result;
   } // end of cprefix()

   /**
    * Unify this spec with t starting from the last symbol,
    * return null, if it is impossible.
    */
   Spec unify (Spec t, TypeSystem ts, SpecSet ss) {
      if (t == null) return null;
      int p1 = leftSide.size();
      int p2 = rightSide.size();
      int q1 = t.leftSide.size();
      int q2 = t.rightSide.size();
      if (p1 < q1) return null;
      if (p2 < q2) return null;
      Spec result = (Spec)this.clone();
      result.incrementWild (0);
      result.maxPosIndex = result.maxPos();
      Spec tc = (Spec)t.clone();
      tc.incrementWild (result.maxPosIndex);
      tc.maxPosIndex = tc.maxPos();
      if (q1 > 0) {
         for (int i=0; i<q1; i++) {
            TypeSymbol m1 = (TypeSymbol)result.leftSide.get (i);
            TypeSymbol m2 = (TypeSymbol)tc.leftSide.get (i);
            int rel = ts.relation (m1.ftype, m2.ftype);
            TypeSymbol mnew = null;        
            switch (rel) {
               case 0: // type conflict
                  return null;
               case 1: // m1 win
                  mnew = new TypeSymbol (m1.ftype, ++tc.maxPosIndex);
                  break;
               case 2: // m2 win
                  mnew = new TypeSymbol (m2.ftype, ++tc.maxPosIndex);
                  break;
               case 3: // equal types
                  mnew = new TypeSymbol (m1.ftype, ++tc.maxPosIndex);
                  break;
               default: throw new RuntimeException (" no relation!!!");
            }
            result.substitute (m1, mnew);
            result.substitute (m2, mnew);
            tc.substitute (m1, mnew);
            tc.substitute (m2, mnew);
         }
      }
      if (q2 > 0) {
         for (int i=0; i<q2; i++) {
            TypeSymbol m1 = (TypeSymbol)result.rightSide.get (i);
            TypeSymbol m2 = (TypeSymbol)tc.rightSide.get (i);
            int rel = ts.relation (m1.ftype, m2.ftype);
            TypeSymbol mnew = null;        
            switch (rel) {
               case 0: // type conflict
                  return null;
               case 1: // m1 win
                  mnew = new TypeSymbol (m1.ftype, ++tc.maxPosIndex);
                  break;
               case 2: // m2 win
                  mnew = new TypeSymbol (m2.ftype, ++tc.maxPosIndex);
                  break;
               case 3: // equal types
                  mnew = new TypeSymbol (m1.ftype, ++tc.maxPosIndex);
                  break;
               default: throw new RuntimeException (" no relation!!!");
            }
            result.substitute (m1, mnew);
            result.substitute (m2, mnew);
            tc.substitute (m1, mnew);
            tc.substitute (m2, mnew);
         }
      result.maxPosIndex = tc.maxPosIndex;
      }
      SpecList tmp = new SpecList();
      tmp.add (result);
      result = tmp.normalize (result, ts, ss);
      return result;
   } // end of unify

   /**
    * Converts stack effect to string.
    */
   public String toString() {
      StringBuffer result = new StringBuffer ("");
      result.append ("( ");
      result.append (leftSide.toString());
      result.append (" --  ");
      result.append (rightSide.toString());
      result.append (") ");
      return result.toString();
   } // end of toString()

   /**
    * Clones this spec cloning leftside and rightside.
    */
   public Object clone () {
      Tvector newleft = (Tvector)leftSide.clone(); // Vector clone is deep?
      Tvector newright = (Tvector)rightSide.clone();
      return new Spec (newleft, newright, ts, parseString,
         maxPosIndex);
   } // end of clone()

   /**
    * Increments indices of this effect by a given number and
    * replaces zero indices by unique indices.
    * @param amount increment
    */
   void incrementWild (int amount) {
      TypeSymbol current;
      leftSide = (Tvector)leftSide.clone(); // !!! .clone();
      rightSide = (Tvector)rightSide.clone(); // !!! .clone();
      Iterator<TypeSymbol> lit = leftSide.iterator();
      Iterator<TypeSymbol> rit = rightSide.iterator();
      while (lit.hasNext()) {
         current = (TypeSymbol)lit.next();
         if (current.position > 0) current.position += amount;
      }
      while (rit.hasNext()) {
         current = (TypeSymbol)rit.next();
         if (current.position > 0) current.position += amount;
      }
      maxPosIndex = maxPos() + amount; // sometimes this is too much!!!
      lit = leftSide.iterator();
      rit = rightSide.iterator();
      while (lit.hasNext()) {
         current = (TypeSymbol)lit.next();
         if (current.position == 0)
            current.position = ++maxPosIndex;
      }
      while (rit.hasNext()) {
         current = (TypeSymbol)rit.next();
         if (current.position == 0)
            current.position = ++maxPosIndex;
      }
   } // end of incrementWild()

   /**
    * Calculates maximal index used in this spec.
    * return maximal index
    */
   int maxPos() {
      TypeSymbol current;
      int m = 0;
      Iterator<TypeSymbol> lit = leftSide.iterator();
      Iterator<TypeSymbol> rit = rightSide.iterator();
      while (lit.hasNext()) {
         current = (TypeSymbol)lit.next();
         if (current.position > m) m = current.position;
      }
      while (rit.hasNext()) {
         current = (TypeSymbol)rit.next();
         if (current.position > m) m = current.position;
      }
      maxPosIndex = m;
      return m;
   } // end of maxPos()

   /**
    * Substitutes clone of given wildcard symbol to another one
    * in this spec.
    * @param oldsym symbol to find
    * @param newsym replacement symbol
    * @return number of replacements made
    */
   int substitute (TypeSymbol oldsym, TypeSymbol newsym) {
      // System.out.println ("In "+toString()+" replace "+oldsym.toString()+
      //    " to "+newsym.toString());
      return leftSide.substitute (oldsym, newsym) +
         rightSide.substitute (oldsym, newsym);
   } // end of substitute()

} // end of Spec

// end of file


