import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [done, setDone] = useState(false);
  const [loading, setLoading] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      // Always resolves (backend never reveals whether the email exists).
      await api("/auth/password/forgot", { method: "POST", body: { email } });
    } catch {
      /* intentionally ignored */
    } finally {
      setLoading(false);
      setDone(true);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/30 px-4">
      <Card className="w-full max-w-md">
        <CardHeader><CardTitle>找回密码</CardTitle></CardHeader>
        <CardContent>
          {done ? (
            <div className="space-y-4 text-sm">
              <p>如果该邮箱已注册，我们已发送重置链接（30 分钟内有效），请查收邮箱。</p>
              <Link to="/login" className="text-primary underline">返回登录</Link>
            </div>
          ) : (
            <form onSubmit={submit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="email">邮箱</Label>
                <Input id="email" type="email" value={email}
                  onChange={(e) => setEmail(e.target.value)} required />
              </div>
              <Button type="submit" className="w-full" disabled={loading}>
                {loading ? "发送中..." : "发送重置链接"}
              </Button>
              <div className="text-center text-sm">
                <Link to="/login" className="text-muted-foreground underline">返回登录</Link>
              </div>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
