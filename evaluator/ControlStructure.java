// file: ControlStructure.java

package evaluator;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Declarative compile-time control structure with a small effect algebra.
 * @author Codex
 * @version 0.2
 * @since 1.5
 */
public class ControlStructure {

   static final String SEGMENT_BODY = "BODY";
   static final String SEGMENT_ALPHA = "ALPHA";
   static final String SEGMENT_BETA = "BETA";
   static final String SEGMENT_EMPTY = "EMPTY";

   String name;
   String syntaxText = "";
   String openRole;
   String midRole = "";
   boolean midOptional = false;
   String closeRole;
   LinkedList<String> segmentNames = new LinkedList<String>();
   LinkedList<String> boundaryRoles = new LinkedList<String>();
   LinkedList<Boolean> boundaryOptional = new LinkedList<Boolean>();
   EffectExpr meaning;
   String compilationText = "";
   String runtimeText = "";
   String meaningText = "";
   SourceSpan sourceSpan;
   boolean builtin = false;

   ControlStructure (String structureName) {
      name = structureName == null ? "" : structureName;
   } // end of constructor

   ControlStructure withOpenRole (String role) {
      openRole = role == null ? "" : role;
      return this;
   } // end of withOpenRole()

   ControlStructure withSyntaxText (String text) {
      syntaxText = text == null ? "" : text;
      return this;
   } // end of withSyntaxText()

   ControlStructure withMidRole (String role, boolean optional) {
      midRole = role == null ? "" : role;
      midOptional = optional && hasMidRole();
      return this;
   } // end of withMidRole()

   ControlStructure withCloseRole (String role) {
      closeRole = role == null ? "" : role;
      return this;
   } // end of withCloseRole()

   ControlStructure withMeaning (EffectExpr expr, String text) {
      meaning = expr;
      if (text == null) {
         meaningText = "";
      } else {
         meaningText = text;
      }
      return this;
   } // end of withMeaning()

   ControlStructure withCompilationText (String text) {
      compilationText = text == null ? "" : text;
      return this;
   } // end of withCompilationText()

   ControlStructure withRuntimeText (String text) {
      runtimeText = text == null ? "" : text;
      return this;
   } // end of withRuntimeText()

   ControlStructure withSourceSpan (SourceSpan span) {
      sourceSpan = span;
      return this;
   } // end of withSourceSpan()

   ControlStructure withBuiltin (boolean flag) {
      builtin = flag;
      return this;
   } // end of withBuiltin()

   ControlStructure withLegacySegments (String first, String second) {
      segmentNames.clear();
      segmentNames.add (canonicalSegmentName (first, SEGMENT_BODY));
      if ((second != null) && (second.trim().length() > 0))
         segmentNames.add (canonicalSegmentName (second, SEGMENT_BETA));
      return this;
   } // end of withLegacySegments()

   ControlStructure withSegmentNames (String first, String second) {
      return withLegacySegments (first, second);
   } // end of withSegmentNames()

   ControlStructure addBoundary (String role, boolean optional,
      String nextSegment) {
      boundaryRoles.add (role == null ? "" : role);
      boundaryOptional.add (Boolean.valueOf (optional));
      segmentNames.add (canonicalSegmentName (nextSegment,
         fallbackSegmentName (segmentNames.size())));
      syncLegacyPattern();
      return this;
   } // end of addBoundary()

   ControlStructure setPattern (String open, LinkedList<String> boundaries,
      LinkedList<Boolean> optionalFlags, LinkedList<String> segments,
      String close) {
      openRole = open == null ? "" : open;
      closeRole = close == null ? "" : close;
      boundaryRoles = cloneStringList (boundaries);
      boundaryOptional = cloneBooleanList (optionalFlags);
      segmentNames = cloneStringList (segments);
      if (segmentNames.size() == 0)
         segmentNames.add (fallbackSegmentName (0));
      syncLegacyPattern();
      return this;
   } // end of setPattern()

   void ensurePatternFromLegacy() {
      if (segmentNames.size() == 0)
         segmentNames.add (hasMidRole() ? SEGMENT_ALPHA : SEGMENT_BODY);
      if ((boundaryRoles.size() == 0) && hasMidRole()) {
         boundaryRoles.add (midRole);
         boundaryOptional.add (Boolean.valueOf (midOptional));
      }
      if ((boundaryRoles.size() > 0) && (segmentNames.size() < 2))
         segmentNames.add (SEGMENT_BETA);
      if (segmentNames.size() == 0)
         segmentNames.add (SEGMENT_BODY);
      while (segmentNames.size() < boundaryRoles.size() + 1) {
         segmentNames.add (fallbackSegmentName (segmentNames.size()));
      }
      syncLegacyPattern();
   } // end of ensurePatternFromLegacy()

   void syncLegacyPattern() {
      if (boundaryRoles.size() == 1) {
         midRole = (String)boundaryRoles.getFirst();
         midOptional = ((Boolean)boundaryOptional.getFirst()).booleanValue();
      } else if (boundaryRoles.size() == 0) {
         midRole = "";
         midOptional = false;
      } else {
         midRole = "";
         midOptional = false;
      }
   } // end of syncLegacyPattern()

   boolean hasMidRole() {
      return (midRole != null) && (midRole.length() > 0);
   } // end of hasMidRole()

   int boundaryCount() {
      return boundaryRoles.size();
   } // end of boundaryCount()

   int segmentCount() {
      return segmentNames.size();
   } // end of segmentCount()

   String segmentNameAt (int index) {
      return (String)segmentNames.get (index);
   } // end of segmentNameAt()

   String boundaryRoleAt (int index) {
      return (String)boundaryRoles.get (index);
   } // end of boundaryRoleAt()

   boolean boundaryOptionalAt (int index) {
      return ((Boolean)boundaryOptional.get (index)).booleanValue();
   } // end of boundaryOptionalAt()

   boolean canAdvanceWithRole (String role, int seenBoundaries) {
      if (seenBoundaries < 0 || seenBoundaries >= boundaryCount()) return false;
      return safeEquals (boundaryRoleAt (seenBoundaries), role);
   } // end of canAdvanceWithRole()

   boolean canCloseWithRole (String role, int seenBoundaries) {
      if (!safeEquals (closeRole, role)) return false;
      return canSkipTailFrom (seenBoundaries);
   } // end of canCloseWithRole()

   boolean canSkipTailFrom (int seenBoundaries) {
      for (int i = seenBoundaries; i < boundaryCount(); i++) {
         if (!boundaryOptionalAt (i)) return false;
      }
      return true;
   } // end of canSkipTailFrom()

   boolean usesRole (String role) {
      if (role == null) return false;
      if (role.equals (openRole)) return true;
      if (role.equals (closeRole)) return true;
      Iterator<String> it = boundaryRoles.iterator();
      while (it.hasNext()) {
         if (role.equals ((String)it.next())) return true;
      }
      return false;
   } // end of usesRole()

   boolean sameSignature (ControlStructure other) {
      if (other == null) return false;
      if (!safeEquals (openRole, other.openRole)) return false;
      if (!safeEquals (closeRole, other.closeRole)) return false;
      if (boundaryRoles.size() != other.boundaryRoles.size()) return false;
      for (int i = 0; i < boundaryRoles.size(); i++) {
         if (!safeEquals ((String)boundaryRoles.get (i),
               (String)other.boundaryRoles.get (i)))
            return false;
         if (boundaryOptionalAt (i) != other.boundaryOptionalAt (i))
            return false;
      }
      return true;
   } // end of sameSignature()

   int segmentIndexOf (String name) {
      if (name == null) return -1;
      String key = canonicalSegmentName (name, "");
      for (int i = 0; i < segmentNames.size(); i++) {
         if (key.equals ((String)segmentNames.get (i))) return i;
      }
      if ((segmentNames.size() == 1) && SEGMENT_BODY.equals (key)) return 0;
      if ((segmentNames.size() > 0) && SEGMENT_ALPHA.equals (key)) return 0;
      if ((segmentNames.size() > 1) && SEGMENT_BETA.equals (key)) return 1;
      return -1;
   } // end of segmentIndexOf()

   String [] labelRoles (int seenBoundaries) {
      String [] result = new String [seenBoundaries + 2];
      result [0] = openRole;
      for (int i = 0; i < seenBoundaries; i++)
         result [i + 1] = boundaryRoleAt (i);
      result [result.length - 1] = closeRole;
      return result;
   } // end of labelRoles()

   static boolean safeEquals (String a, String b) {
      if (a == null) return b == null;
      return a.equals (b);
   } // end of safeEquals()

   static String canonicalSegmentName (String text, String fallback) {
      if (text == null || text.trim().length() == 0) return fallback;
      StringBuffer result = new StringBuffer ("");
      String trimmed = text.trim();
      for (int i = 0; i < trimmed.length(); i++) {
         char current = trimmed.charAt (i);
         if (Character.isLetterOrDigit (current))
            result.append (Character.toUpperCase (current));
         else
            result.append ('_');
      }
      String canonical = result.toString();
      if (canonical.length() == 0) return fallback;
      return canonical;
   } // end of canonicalSegmentName()

   static String fallbackSegmentName (int index) {
      if (index <= 0) return SEGMENT_BODY;
      if (index == 1) return SEGMENT_BETA;
      return "SEGMENT_" + String.valueOf (index + 1);
   } // end of fallbackSegmentName()

   static LinkedList<String> cloneStringList (LinkedList<String> source) {
      if (source == null) return new LinkedList<String>();
      return new LinkedList<String> (source);
   } // end of cloneStringList()

   static LinkedList<Boolean> cloneBooleanList (LinkedList<Boolean> source) {
      if (source == null) return new LinkedList<Boolean>();
      return new LinkedList<Boolean> (source);
   } // end of cloneBooleanList()

   static abstract class EffectExpr {
   } // end of EffectExpr

   static class EmptyExpr extends EffectExpr {
   } // end of EmptyExpr

   static class SegmentExpr extends EffectExpr {
      String segmentName;

      SegmentExpr (String name) {
         segmentName = name == null ? "" : name;
      } // end of constructor
   } // end of SegmentExpr

   static class ControlExpr extends EffectExpr {
      String role;

      ControlExpr (String controlRole) {
         role = controlRole == null ? "" : controlRole;
      } // end of constructor
   } // end of ControlExpr

   static class SeqExpr extends EffectExpr {
      LinkedList<EffectExpr> parts = new LinkedList<EffectExpr>();
   } // end of SeqExpr

   static class GlbExpr extends EffectExpr {
      EffectExpr left;
      EffectExpr right;

      GlbExpr (EffectExpr lhs, EffectExpr rhs) {
         left = lhs;
         right = rhs;
      } // end of constructor
   } // end of GlbExpr

   static class StarExpr extends EffectExpr {
      EffectExpr inner;

      StarExpr (EffectExpr expr) {
         inner = expr;
      } // end of constructor
   } // end of StarExpr

} // end of ControlStructure

// end of file
