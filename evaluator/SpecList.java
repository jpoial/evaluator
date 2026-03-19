
// file: SpecList.java

package evaluator;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * List of specifications (sequence of stack-effects).
 * @author Jaanus Poial
 * @version 0.6
 * @since 1.5
 */
public class SpecList extends LinkedList<Spec> {
    
    static final long serialVersionUID = 0xaabbcc;

   static class NormInfo {
      int occurrences = 0;
      int assignedIndex = 0;
      boolean explicitIndex = false;
   } // end of NormInfo

   /** workfield to keep current maximal index */
   int cMaxInd = 0;

   /**
    * Creates initial specificationlist for the program.
    * @param p program
    * @param ts  type system to use
    * @param ss  set of specifications to use
    */
   SpecList (ProgText p, TypeSystem ts, SpecSet ss) {
      Iterator<String> pit = p.iterator();
      String s, key;
      Spec sp;
      while (pit.hasNext()) {
         s = (String)pit.next();
         // key = (s.trim().split ("[\t \r\n\f]"))[0]; // JDK 1.4!!!
         key = s.trim(); // earlier JDK
         sp = (Spec)ss.get (key);
         if (sp == null)
            throw new RuntimeException ("no specif. found for " + key);
         add ((Spec)sp.clone());
      }
   } // end of constructor

   SpecList() {
      super();
      cMaxInd = 0;
   }

   /**
    * Substitutes clone of given wildcard symbol to another one for
    * this list.
    * @param oldsym symbol to find
    * @param newsym replacement symbol
    * @return number of replacements made
    */
   int substitute (TypeSymbol oldsym, TypeSymbol newsym) {
      int result = 0;
      Iterator<Spec> it = iterator();
      while (it.hasNext()) {
         ((Spec)it.next()).substitute (oldsym, newsym);
      }
      return result;
   } // end of substitute()

   /**
    * Evaluates this list using stack-effect calculus.
    * @param ts  type system to use
    * @param ss  set of specifications to use
    * @return resulting specification (list itself is also modified!)
    */
   public Spec evaluate (TypeSystem ts, SpecSet ss) {
      Iterator<Spec> it = iterator();
      while (it.hasNext()) {
         Spec current = (Spec)it.next();
         // System.out.println (current.toString()+ " is incremented by "+
         //    String.valueOf (cMaxInd));
         current.incrementWild (cMaxInd);
         cMaxInd = current.maxPos();
         // System.out.println ("  resulting in "+current.toString()+
         //    " where cMaxInd became "+String.valueOf (cMaxInd));
      }
      Spec result = new Spec (new Tvector(), new Tvector(), ts, "", 0);
      int llen = size();
      int i = 0;
      Spec second;
      while (i < llen) {
         second = (Spec)get (i);
         result = multiply (result, second, ts);
         // System.out.println ("Multiply used "+second.toString());
         if (result == null) {
            System.out.println
            // throw new RuntimeException
               ("Type conflict for " + second.toString() + "!!!");
         }
         i++;
      }
      if (result != null) result = normalize (result, ts, ss);
      return result;
   } // end of evaluate()

   /**
    * Calculates the product of given stack-effects.
    * @param s1 accumulator
    * @param s2 second operand
    * @param ts current typesystem
    * @return product (and this list changes)
    */
   Spec multiply (Spec s1, Spec s2, TypeSystem ts) {
      if (s1 == null) return null;
      if (s2 == null) return null;
      Tvector rleft =(Tvector)s1.leftSide.clone();
      Tvector rright = (Tvector)s2.rightSide.clone();
      if (s1.rightSide.size()==0) {
         rleft.addAll (0, s2.leftSide);
      } else {
         if (s2.leftSide.size()==0) {
            rright.addAll (0, s1.rightSide);
         } else {
            TypeSymbol m1 = (TypeSymbol)s1.rightSide.lastElement();
            TypeSymbol m2 = (TypeSymbol)s2.leftSide.lastElement();
            int rel = ts.relation (m1.ftype, m2.ftype);
            TypeSymbol mnew = null;
            switch (rel) {
               case 0: // type conflict
                  System.out.println ("Conflict between " +
                     m1.toString() + " and " + m2.toString() +
                     " in " + s1.toString() + " x " + s2.toString() );
                  return null;
               case 1:  // m1 win
                  mnew = new TypeSymbol (m1.ftype, ++cMaxInd,
                     m1.explicitIndex | m2.explicitIndex);
                  break;
               case 2:  // m2 win
                  mnew = new TypeSymbol (m2.ftype, ++cMaxInd,
                     m1.explicitIndex | m2.explicitIndex);
                  break;
               case 3:  // equal types
                  mnew = new TypeSymbol (m1.ftype, ++cMaxInd,
                     m1.explicitIndex | m2.explicitIndex);
                  break;
               default: throw new RuntimeException ("no relation!!!");
            }
            // System.out.println (m1.toString()+" meets "+m2.toString()+
            //    " resulting in "+ mnew.toString());
            Tvector r1rs = (Tvector)s1.rightSide.clone();
            Tvector r2ls = (Tvector)s2.leftSide.clone();
            r1rs.substitute (m1, mnew);
            r1rs.substitute (m2, mnew);
            r2ls.substitute (m1, mnew);
            r2ls.substitute (m2, mnew);
            rleft.substitute (m1, mnew);
            rleft.substitute (m2, mnew);
            rright.substitute (m1, mnew);
            rright.substitute (m2, mnew);
            substitute (m1, mnew);
            substitute (m2, mnew);
            r1rs.removeElementAt (r1rs.size()-1);
            r2ls.removeElementAt (r2ls.size()-1);
            Spec r1 = new Spec (rleft, r1rs, ts, "", 0);
            Spec r2 = new Spec (r2ls, rright, ts, "", 0);
            return multiply (r1, r2, ts);
         };
      };
      return new Spec (rleft, rright, ts,
         s1.parseString+" "+s2.parseString,
         Math.max (s1.maxPosIndex, s2.maxPosIndex));
   } // end of multiply()

   /**
    * Converts wildcard indices for better readability.
    * @param s  old value of the product
    * @param ts  current typesystem
    * @param ss  current set of specifications
    * @return   new value of the product (and this list canges)
    */
   Spec normalize (Spec s, TypeSystem ts, SpecSet ss) {
      Spec result = s;
      int max = result.maxPos();
      Iterator<Spec> it = iterator();
      while (it.hasNext()) {
         max = Math.max (max, ((Spec)it.next()).maxPos());
      }
      max++; // now max is big enough
      it = iterator();
      while (it.hasNext()) {
         ((Spec)it.next()).incrementWild (max);
      }
      result.incrementWild (max);
      // now everything is bigger than possible new indices
      int newInd = 0;
      Hashtable<TypeSymbol, NormInfo> subst =
          new Hashtable<TypeSymbol, NormInfo>();
      TypeSymbol t = null;
      Spec sp = null;
      Tvector tv = null;
      // first pass
      tv = result.leftSide;
      Iterator<TypeSymbol> it2 = tv.iterator();
      while (it2.hasNext()) {
         t = (TypeSymbol)it2.next();
         addts (subst, t, -1);
      }
      tv = result.rightSide;
      it2 = tv.iterator();
      while (it2.hasNext()) {
         t = (TypeSymbol)it2.next();
         addts (subst, t, -1);
      }
      Iterator<Spec> spit = iterator();
      while (spit.hasNext()) {
         sp = (Spec)spit.next();
         tv = sp.leftSide;
         it2 = tv.iterator();
         while (it2.hasNext()) {
            t = (TypeSymbol)it2.next();
            addts (subst, t, -1);
         }
         tv = sp.rightSide;
         it2 = tv.iterator();
         while (it2.hasNext()) {
            t = (TypeSymbol)it2.next();
            addts (subst, t, -1);
         }
      }
      // second pass
      tv = result.leftSide;
      for (int i = tv.size()-1; i >= 0; i--) {
         t = (TypeSymbol)tv.get (i);
         newInd = addts (subst, t, newInd);
      }
      tv = result.rightSide;
      it2 = tv.iterator();
      while (it2.hasNext()) {
         t = (TypeSymbol)it2.next();                 
         newInd = addts (subst, t, newInd);
      }
      spit = iterator();
      while (spit.hasNext()) {
         sp = (Spec)spit.next();
         tv = sp.leftSide;
         for (int i = tv.size()-1; i >= 0; i--) {
            t = (TypeSymbol)tv.get (i);
            newInd = addts (subst, t, newInd);
         }
         tv = sp.rightSide;
         it2 = tv.iterator();
         while (it2.hasNext()) {
            t = (TypeSymbol)it2.next();
            newInd = addts (subst, t, newInd);
         }
      }
      /* let us substitute now */
      Iterator<TypeSymbol>it3 = subst.keySet().iterator();
      while (it3.hasNext()) {
         t = (TypeSymbol)it3.next(); // key
         NormInfo info = (NormInfo)subst.get (t);
         int pos = 0;
         if (needsIndex (info)) pos = info.assignedIndex;
         TypeSymbol newt = new TypeSymbol (t.ftype, pos, info.explicitIndex);
         result.substitute (t, newt);
         substitute (t, newt);
      }
      return result;
   } // end of normalize()

   /**
    * Manages hashtable of substitutions for typesymbols (inner
    * function of normalize() method).
    * @param table hashtable with typesymbols as keys and metadata values
    * @param key typesymbol to add/replace in table
    * @param index index value before possible adding,
    *   -1 means first pass and non-negative means second pass
    * @return new index value
    */
   static int addts (Hashtable<TypeSymbol, NormInfo> table, TypeSymbol key,
      int index) {
      int result = index;
      if (index == -1) { // first pass
         NormInfo info = (NormInfo)table.get (key);
         if (info == null) {
            info = new NormInfo();
            table.put (key, info);
         }
         info.occurrences++;
         info.explicitIndex = info.explicitIndex | key.explicitIndex;
      } else {  // second pass
         NormInfo info = (NormInfo)table.get (key);
         if (info != null) {
            if (needsIndex (info) & (info.assignedIndex == 0)) {
               result++;
               info.assignedIndex = result;
            } else { // keep already assigned or hidden indices
            }
         } else {
            throw new RuntimeException ("Key " + key.toString() 
               + " not present!!!");
         }
      }
      return result;
   } // end of addts()

   /**
    * Tells whether a symbol should keep a visible wildcard index after
    * normalization.
    * @param info accumulated metadata for one symbol
    * @return true if the wildcard index should stay visible
    */
   static boolean needsIndex (NormInfo info) {
      return info.explicitIndex | (info.occurrences > 2);
   } // end of needsIndex()

} // end of SpecList

// end of file
