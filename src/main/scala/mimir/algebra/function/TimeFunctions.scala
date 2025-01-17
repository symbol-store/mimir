package mimir.algebra.function;

import org.joda.time.DateTime
import mimir.algebra._

object TimeFunctions
{
  def register(fr: FunctionRegistry)
  {


    fr.register(ID("year_part"), 
      { 
        case Seq(TimestampPrimitive(y, _, _, _, _, _, _)) => IntPrimitive(y)
        case Seq(DatePrimitive(y, _, _)) => IntPrimitive(y)
        case Seq(IntervalPrimitive(p)) => IntPrimitive(p.getYears())
        case Seq(x) => throw new Exception(s"Invalid Time Primitive '$x'")
        case Seq() => throw new Exception(s"EXTRACT needs an argument")
        case _ => throw new Exception("Too many arguments to EXTRACT")
      },
      { case Seq(TTimestamp() | TDate() | TInterval()) => TInt() }
    )
    fr.register(ID("month_part"), 
      { 
        case Seq(TimestampPrimitive(_, m, _, _, _, _, _)) => IntPrimitive(m)
        case Seq(DatePrimitive(_, m, _)) => IntPrimitive( m)
        case Seq(IntervalPrimitive(p)) => IntPrimitive(p.getYears() * 12 + p.getMonths())
        case Seq(x) => throw new Exception(s"Invalid Time Primitive '$x'")
        case Seq() => throw new Exception(s"EXTRACT needs an argument")
        case _ => throw new Exception("Too many arguments to EXTRACT")
      },
      { case Seq(TTimestamp() | TDate() | TInterval()) => TInt() }
    )
    fr.register(ID("week_part"), 
      { 
        case Seq(IntervalPrimitive(p)) => IntPrimitive(p.toStandardWeeks().getWeeks())
        case Seq(x) => throw new Exception(s"Invalid Time Primitive '$x'")
        case Seq() => throw new Exception(s"EXTRACT needs an argument")
        case _ => throw new Exception("Too many arguments to EXTRACT")
      },
      { case Seq(TInterval()) => TInt() }
    )
    fr.register(ID("day_part"), 
      { 
        case Seq(TimestampPrimitive(_, _, d, _, _, _, _)) => IntPrimitive(d)
        case Seq(DatePrimitive(_, _, d)) => IntPrimitive(d)
        case Seq(IntervalPrimitive(p)) => IntPrimitive(p.toStandardDays().getDays())
        case Seq(x) => throw new Exception(s"Invalid Time Primitive '$x'")
        case Seq() => throw new Exception(s"EXTRACT needs an argument")
        case _ => throw new Exception("Too many arguments to EXTRACT")
      },
      { case Seq(TTimestamp() | TDate() | TInterval()) => TInt() }
    )
    fr.register(ID("hour_part"), 
      { 
        case Seq(TimestampPrimitive(_, _, _, hh, _, _, _)) => IntPrimitive(hh)
        case Seq(DatePrimitive(_, _, _)) => IntPrimitive(0)
        case Seq(IntervalPrimitive(p)) => IntPrimitive(p.toStandardHours().getHours())
        case Seq(x) => throw new Exception(s"Invalid Time Primitive '$x'")
        case Seq() => throw new Exception(s"EXTRACT needs an argument")
        case _ => throw new Exception("Too many arguments to EXTRACT")
      },
      { case Seq(TTimestamp() | TDate() | TInterval()) => TInt() }
    )
    fr.register(ID("minute_part"), 
      { 
        case Seq(TimestampPrimitive(_, _, _, _, mm, _, _)) => IntPrimitive(mm)
        case Seq(DatePrimitive(_, _, _)) => IntPrimitive(0)
        case Seq(IntervalPrimitive(p)) => IntPrimitive(p.toStandardMinutes().getMinutes())
        case Seq(x) => throw new Exception(s"Invalid Time Primitive '$x'")
        case Seq() => throw new Exception(s"EXTRACT needs an argument")
        case _ => throw new Exception("Too many arguments to EXTRACT")
      },
      { case Seq(TTimestamp() | TDate() | TInterval()) => TInt() }
    )
    fr.register(ID("second_part"), 
      { 
        case Seq(TimestampPrimitive(_, _, _, _, _, ss, _)) => IntPrimitive(ss)
        case Seq(DatePrimitive(_, _, _)) => IntPrimitive(0)
        case Seq(IntervalPrimitive(p)) => IntPrimitive(p.toStandardSeconds().getSeconds())
        case Seq(x) => throw new Exception(s"Invalid Time Primitive '$x'")
        case Seq() => throw new Exception(s"EXTRACT needs an argument")
        case _ => throw new Exception("Too many arguments to EXTRACT")
      },
      { case Seq(TTimestamp() | TDate() | TInterval()) => TInt() }
    )
    fr.register(ID("millisecond_part"), 
      { 
        case Seq(TimestampPrimitive(_, _, _, _, _, _, ms)) => IntPrimitive(ms)
        case Seq(DatePrimitive(_, _, _)) => IntPrimitive(0)
        case Seq(IntervalPrimitive(p)) => IntPrimitive(p.toStandardSeconds().getSeconds() * 1000)
        case Seq(x) => throw new Exception(s"Invalid Time Primitive '$x'")
        case Seq() => throw new Exception(s"EXTRACT needs an argument")
        case _ => throw new Exception("Too many arguments to EXTRACT")
      },
      { case Seq(TTimestamp() | TDate() | TInterval()) => TInt() }
    )
    fr.register(ID("second"), 
      { 
        case Seq(TimestampPrimitive(_, _, _, _, _, ss, _)) => ???
        case Seq(DatePrimitive(_, _, _)) => ???
        case Seq(IntervalPrimitive(p)) => ???
        case Seq(x) => ???
        case Seq() => throw new Exception(s"EXTRACT needs an argument")
        case _ => throw new Exception("Too many arguments to EXTRACT")
      },
      { case Seq(TTimestamp() | TDate() | TInterval()) => TInt() }
    )
    fr.register(ID("year"), 
      { 
        case Seq(TimestampPrimitive(_, _, _, _, _, ss, _)) => ???
        case Seq(DatePrimitive(_, _, _)) => ???
        case Seq(IntervalPrimitive(p)) => ???
        case Seq(x) => ???
        case Seq() => throw new Exception(s"EXTRACT needs an argument")
        case _ => throw new Exception("Too many arguments to EXTRACT")
      },
      { case Seq(TTimestamp() | TDate() | TInterval()) => TInt() }
    )
  }
}