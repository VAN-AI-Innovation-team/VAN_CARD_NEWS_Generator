import { useEffect, useState } from 'react';
import axios from 'axios';
// 임시 테스트용 컴포넌트 import 추가
import PostInputForm from './components/PostForm/PostInputForm';
function App() {
  const [message, setMessage] = useState('연동 확인 중...');

  useEffect(() => {
    axios
      .get('/api/health')
      .then((res) => {
        setMessage(res.data.message);
      })
      .catch((err) => {
        console.error('CORS 또는 네트워크 에러 발생:', err);
        setMessage('통신 실패! F12 콘솔창을 확인하세요.');
      });
  }, []);

  return (
    <div style={{ padding: '40px', fontFamily: 'sans-serif' }}>
      <h1>🤝 프론트-백엔드 연동 테스트</h1>
      <p>
        백엔드 응답 결과: <strong style={{ color: 'blue' }}>{message}</strong>
      </p>
    </div>
  );
}

export default App;
